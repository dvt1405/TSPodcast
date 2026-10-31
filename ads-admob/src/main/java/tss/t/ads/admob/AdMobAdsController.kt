package tss.t.ads.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tss.t.ads.AdFormat
import tss.t.ads.AdPlacement
import tss.t.ads.AdPolicy
import tss.t.ads.AdsAnalytics
import tss.t.ads.AdsConfig
import tss.t.ads.AdsController
import tss.t.ads.AdsHostSignals
import tss.t.ads.AdsState
import tss.t.ads.AdCounterStore
import tss.t.ads.Decision
import tss.t.ads.RewardResult
import tss.t.ads.TriggerContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * AdMob (GMA Next-Gen SDK) implementation of [AdsController].
 *
 * Start-up: `start(activity)` from the host Activity → UMP consent update (and
 * form if required) → `MobileAds.initialize` on a background thread → start
 * the preloaders for the enabled full-screen placements.
 *
 * App Open (warm start only): the process going to background is recorded on
 * ProcessLifecycleOwner ON_STOP; on the next ON_START the decision is deferred
 * to the host Activity's onResume, so `onNewIntent` (media notification, deep
 * link) has already been delivered. It shows only an ad that is ALREADY
 * preloaded, and only if [AdPolicy] allows it (never while audio is
 * playing/buffering, never on the Player, never on a notification/deep-link
 * launch, caps and gaps from Remote Config).
 *
 * Threading: public methods are called on the main thread. SDK callbacks
 * arrive on background threads and only touch StateFlows, atomics and the
 * synchronized counter store; anything that touches a View or an Activity is
 * posted to the main thread.
 */
@Singleton
class AdMobAdsController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val consent: ConsentManager,
    private val config: AdsConfig,
    private val policy: AdPolicy,
    private val store: AdCounterStore,
    private val host: AdsHostSignals,
    private val analytics: AdsAnalytics,
    private val unitIds: AdUnitIds,
    private val slotCache: AdSlotCache,
) : AdsController, DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AdsState>(AdsState.Idle)
    override val state: StateFlow<AdsState> = _state.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Consent allows requests AND the SDK finished initialising. Read by the renderer. */
    private val _canLoadAds = MutableStateFlow(false)
    val canLoadAds: StateFlow<Boolean> = _canLoadAds.asStateFlow()

    private val sdkInitialized = AtomicBoolean(false)
    private val initStarted = AtomicBoolean(false)
    private val observersRegistered = AtomicBoolean(false)
    private val consentInFlight = AtomicBoolean(false)
    private val fullScreenShowing = AtomicBoolean(false)
    private val startedPreloaders = Collections.synchronizedSet(mutableSetOf<AdPlacement>())

    // Main-thread state.
    private var hostActivityClass: Class<out Activity>? = null
    private var backgroundAt = 0L
    private var pendingWarmStartBackgroundMs: Long? = null
    private var externalLaunch = false

    override fun start(activity: Activity) {
        hostActivityClass = activity.javaClass
        if (observersRegistered.compareAndSet(false, true)) {
            activity.application.registerActivityLifecycleCallbacks(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            store.startSession()
        }
        val settings = config.current()
        if (!settings.adsEnabled) {
            _state.value = AdsState.Disabled("kill_switch")
            log("ads disabled by kill switch")
            return
        }
        // Consent obtained in an earlier session already allows requests: start
        // the SDK in parallel with this session's consent refresh (Google's
        // recommended pattern) instead of waiting for the network round trip.
        if (consent.canRequestAds) initializeSdk()

        if (!consentInFlight.compareAndSet(false, true)) return
        if (_state.value !is AdsState.Ready) _state.value = AdsState.GatheringConsent
        consent.gather(activity) { error ->
            consentInFlight.set(false)
            val canRequest = consent.canRequestAds
            _privacyOptionsRequired.value = consent.privacyOptionsRequired
            analytics.consent(consent.consentStatus, canRequest, error?.errorCode)
            log("consent done: status=${consent.consentStatus} canRequestAds=$canRequest error=${error?.message}")
            _state.value = AdsState.Ready(canRequest)
            if (canRequest) initializeSdk()
            updateCanLoad()
        }
    }

    private fun initializeSdk() {
        if (!initStarted.compareAndSet(false, true)) return
        mainScope.launch {
            val ok = withContext(Dispatchers.IO) {
                // Must not run on the main thread (ANR risk, per Google).
                runCatching {
                    val requestConfig = RequestConfiguration.Builder().apply {
                        if (BuildConfig.DEBUG) setTestDeviceIds(testDeviceIds())
                    }.build()
                    val initConfig = InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID)
                        .setRequestConfiguration(requestConfig)
                        .build()
                    MobileAds.initialize(appContext, initConfig) {
                        // Background thread.
                        sdkInitialized.set(true)
                        log("Mobile Ads SDK initialised (${MobileAds.getVersion()})")
                        updateCanLoad()
                        mainScope.launch { startPreloaders() }
                    }
                }.onFailure { Log.e(TAG, "MobileAds.initialize failed", it) }.isSuccess
            }
            if (!ok) _state.value = AdsState.Disabled("init_failed")
        }
    }

    private fun updateCanLoad() {
        _canLoadAds.value = sdkInitialized.get() && consent.canRequestAds &&
                config.current().adsEnabled
    }

    private fun startPreloaders() {
        if (!_canLoadAds.value) return
        val settings = config.current()
        listOf(AdPlacement.AppOpenWarm, AdPlacement.InterstitialNav, AdPlacement.RewardedAdFree)
            .filter { settings.isEnabled(it) && it !in startedPreloaders }
            .forEach { placement ->
                val unitId = unitIds.forPlacement(placement, settings)
                val preload = PreloadConfiguration(AdRequest.Builder(unitId).build())
                val callback = preloadCallback(placement)
                val started = runCatching {
                    when (placement.format) {
                        AdFormat.AppOpen -> AppOpenAdPreloader.start(placement.key, preload, callback)
                        AdFormat.Interstitial ->
                            InterstitialAdPreloader.start(placement.key, preload, callback)

                        AdFormat.Rewarded -> RewardedAdPreloader.start(placement.key, preload, callback)
                        else -> false
                    }
                }.getOrElse { Log.w(TAG, "preloader ${placement.key} failed", it); false }
                if (started) startedPreloaders += placement
                log("preloader ${placement.key} started=$started")
            }
    }

    private fun preloadCallback(placement: AdPlacement) = object : PreloadCallback {
        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
            log("preloaded ${placement.key}")
        }

        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
            analytics.loadFailed(placement, adError.code.name)
            log("preload failed ${placement.key}: ${adError.code} ${adError.message}")
        }
    }

    override fun onExternalLaunch() {
        externalLaunch = true
    }

    // ----- Process lifecycle (main thread) -----

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = System.currentTimeMillis()
        pendingWarmStartBackgroundMs = null
        externalLaunch = false
    }

    override fun onStart(owner: LifecycleOwner) {
        // The initial replay when the observer is added (cold start) has no
        // previous background, so it is never treated as a warm start.
        if (backgroundAt > 0L) {
            pendingWarmStartBackgroundMs = System.currentTimeMillis() - backgroundAt
        }
    }

    // ----- Activity callbacks (main thread) -----

    override fun onActivityResumed(activity: Activity) {
        val backgroundMs = pendingWarmStartBackgroundMs ?: return
        // Not our host (e.g. the SDK's own AdActivity): wait for the host.
        if (activity.javaClass != hostActivityClass) return
        pendingWarmStartBackgroundMs = null
        maybeShowAppOpen(activity, backgroundMs)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity.javaClass == hostActivityClass) slotCache.clearBanners()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun triggerContext(backgroundMs: Long? = null) = TriggerContext(
        canRequestAds = _canLoadAds.value,
        playbackActive = host.isPlaybackActive(),
        onPlayerScreen = host.isOnPlayerScreen(),
        externalLaunch = externalLaunch,
        onboardingCompleted = host.isOnboardingCompleted(),
        backgroundDurationMs = backgroundMs,
    )

    private fun maybeShowAppOpen(activity: Activity, backgroundMs: Long) {
        val placement = AdPlacement.AppOpenWarm
        if (fullScreenShowing.get()) return
        val settings = config.current()
        val decision = policy.canShowFullScreen(placement, settings, triggerContext(backgroundMs))
        if (decision is Decision.Block) {
            analytics.blocked(placement, decision.reason)
            log("app open blocked: ${decision.reason} (background ${backgroundMs / 1000}s)")
            return
        }
        // Only an ad that is already preloaded; never load-then-show.
        val ad = AppOpenAdPreloader.pollAd(placement.key)
        if (ad == null) {
            analytics.blocked(placement, "not_preloaded")
            log("app open skipped: nothing preloaded")
            return
        }
        ad.adEventCallback = object : FullScreenEvents(placement), AppOpenAdEventCallback {}
        fullScreenShowing.set(true)
        log("showing app open")
        ad.show(activity)
    }

    /** Natural breaks seen this process (≈ session), for "every Nth break". */
    private val naturalBreaks = java.util.concurrent.atomic.AtomicInteger(0)

    override fun maybeShowInterstitial(
        activity: Activity,
        placement: AdPlacement,
        screenDwellMs: Long?,
    ): Boolean {
        if (placement.format != AdFormat.Interstitial || fullScreenShowing.get()) return false
        val lastPlayback = host.lastPlaybackAt()
        val ctx = triggerContext().copy(
            naturalBreakIndex = naturalBreaks.incrementAndGet(),
            screenDwellMs = screenDwellMs,
            sincePlaybackMs = lastPlayback.takeIf { it > 0L }
                ?.let { android.os.SystemClock.elapsedRealtime() - it },
        )
        val decision = policy.canShowFullScreen(placement, config.current(), ctx)
        if (decision is Decision.Block) {
            analytics.blocked(placement, decision.reason)
            return false
        }
        val ad = InterstitialAdPreloader.pollAd(placement.key) ?: run {
            analytics.blocked(placement, "not_preloaded")
            return false
        }
        ad.adEventCallback = object : FullScreenEvents(placement), InterstitialAdEventCallback {}
        fullScreenShowing.set(true)
        ad.show(activity)
        return true
    }

    override suspend fun showRewarded(activity: Activity, placement: AdPlacement): RewardResult {
        if (placement.format != AdFormat.Rewarded || fullScreenShowing.get()) {
            return RewardResult.NotAvailable
        }
        val settings = config.current()
        val decision = policy.canShowRewarded(placement, settings, triggerContext())
        if (decision is Decision.Block) {
            analytics.blocked(placement, decision.reason)
            return RewardResult.NotAvailable
        }
        val ad = RewardedAdPreloader.pollAd(placement.key) ?: return RewardResult.NotAvailable
        return suspendCancellableCoroutine { cont ->
            val earned = AtomicReference<RewardResult>(RewardResult.Dismissed)
            ad.adEventCallback = object : FullScreenEvents(placement), RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    super<FullScreenEvents>.onAdDismissedFullScreenContent()
                    if (cont.isActive) cont.resume(earned.get())
                }

                override fun onAdFailedToShowFullScreenContent(
                    fullScreenContentError: FullScreenContentError,
                ) {
                    super<FullScreenEvents>.onAdFailedToShowFullScreenContent(fullScreenContentError)
                    if (cont.isActive) cont.resume(RewardResult.NotAvailable)
                }
            }
            fullScreenShowing.set(true)
            mainScope.launch {
                ad.show(activity) { item ->
                    earned.set(RewardResult.Earned(item.type, item.amount))
                    analytics.rewardEarned(placement)
                    if (placement == AdPlacement.RewardedAdFree) {
                        store.grantAdFreeUntil(
                            System.currentTimeMillis() + settings.rewardedAdFreeMinutes * 60_000L
                        )
                    }
                }
            }
        }
    }

    override fun showPrivacyOptions(activity: Activity) {
        consent.showPrivacyOptions(activity) { error ->
            if (error != null) Log.w(TAG, "privacy options form: ${error.message}")
            _privacyOptionsRequired.value = consent.privacyOptionsRequired
            updateCanLoad()
            if (consent.canRequestAds) initializeSdk()
        }
    }

    /** Shared full-screen callbacks. Called on a background thread. */
    private open inner class FullScreenEvents(private val placement: AdPlacement) : AdEventCallback {
        override fun onAdShowedFullScreenContent() {
            policy.recordShown(placement)
            analytics.show(placement)
        }

        override fun onAdDismissedFullScreenContent() {
            fullScreenShowing.set(false)
        }

        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
            fullScreenShowing.set(false)
            analytics.loadFailed(placement, "show_${fullScreenContentError.code.name}")
        }

        override fun onAdImpression() = analytics.impression(placement)
        override fun onAdClicked() = analytics.click(placement)
        override fun onAdPaid(value: AdValue) = analytics.paid(
            placement, value.valueMicros, value.currencyCode, value.precisionType.name
        )
    }

    private fun testDeviceIds(): List<String> =
        BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "TSAds"
    }
}
