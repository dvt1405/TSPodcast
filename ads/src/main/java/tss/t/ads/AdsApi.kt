package tss.t.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Advertising seam. Nothing in this file (or this module) may reference an ad
 * SDK type: `:app` talks only to these interfaces, and `:ads-admob` implements
 * them. See docs/plans/admob-migration.md §3.
 */
enum class AdFormat { AppOpen, Banner, CollapsibleBanner, Native, Interstitial, Rewarded }

/**
 * Every ad placement in the app. [key] is the stable identifier used in Remote
 * Config (`ads_placements_enabled`, `ads_unit_ids`) and in analytics.
 */
enum class AdPlacement(val key: String, val format: AdFormat) {
    AppOpenWarm("app_open_warm", AdFormat.AppOpen),
    AppOpenCold("app_open_cold", AdFormat.AppOpen),
    DiscoverInline("discover_inline", AdFormat.Native),
    DiscoverFavInline("discover_fav_inline", AdFormat.Banner),
    FavouriteTop("favourite_top", AdFormat.Banner),
    PlayerEpisodeList("player_episode_list", AdFormat.Banner),
    PodcastDetailHeader("podcast_detail_header", AdFormat.Banner),
    PodcastDetailEpisodes("podcast_detail_episodes", AdFormat.Native),
    SearchResults("search_results", AdFormat.Banner),
    DiscoverCollapsible("discover_collapsible", AdFormat.CollapsibleBanner),
    InterstitialNav("interstitial_nav", AdFormat.Interstitial),
    RewardedAdFree("rewarded_ad_free", AdFormat.Rewarded);

    val isFullScreen: Boolean
        get() = format == AdFormat.AppOpen || format == AdFormat.Interstitial ||
                format == AdFormat.Rewarded

    companion object {
        fun fromKey(key: String): AdPlacement? = entries.firstOrNull { it.key == key }
    }
}

sealed interface AdsState {
    /** start() not called yet. */
    data object Idle : AdsState

    /** Waiting for the consent form / consent info update. */
    data object GatheringConsent : AdsState

    /**
     * Consent flow finished. [canRequestAds] false means the user is in a
     * regulated region and has not (yet) given the consent ads need.
     */
    data class Ready(val canRequestAds: Boolean) : AdsState

    /** Ads are off: kill switch, ad-free reward, unsupported install, ... */
    data class Disabled(val reason: String) : AdsState
}

sealed interface RewardResult {
    data class Earned(val type: String, val amount: Int) : RewardResult
    data object Dismissed : RewardResult
    data object NotAvailable : RewardResult
}

/** The only advertising abstraction `:app` is allowed to know. */
interface AdsController {
    val state: StateFlow<AdsState>

    /** True when UMP says the app must offer a "privacy options" entry point. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /**
     * Called from the host Activity's onCreate: consent -> SDK init -> preload.
     * Idempotent; safe to call again after a configuration change.
     */
    fun start(activity: Activity)

    /**
     * The current foreground session was opened from a media notification or a
     * deep link. Suppresses the App Open ad for this foreground session (until
     * the app is backgrounded again). Call from the Activity's intent handling.
     */
    fun onExternalLaunch()

    /**
     * Interstitial at a natural transition point. Returns false when blocked by
     * policy or when no ad is preloaded (never waits for a load).
     */
    fun maybeShowInterstitial(
        activity: Activity,
        placement: AdPlacement,
        screenDwellMs: Long? = null,
    ): Boolean

    /** User-initiated rewarded ad. Never auto-shown. */
    suspend fun showRewarded(activity: Activity, placement: AdPlacement): RewardResult

    fun showPrivacyOptions(activity: Activity)
}

/** Default when no ad implementation module is on the classpath. */
object NoOpAdsController : AdsController {
    private val disabled = MutableStateFlow<AdsState>(AdsState.Disabled("no_implementation"))
    private val noPrivacy = MutableStateFlow(false)
    override val state: StateFlow<AdsState> = disabled.asStateFlow()
    override val privacyOptionsRequired: StateFlow<Boolean> = noPrivacy.asStateFlow()
    override fun start(activity: Activity) = Unit
    override fun onExternalLaunch() = Unit
    override fun maybeShowInterstitial(activity: Activity, placement: AdPlacement, screenDwellMs: Long?) = false
    override suspend fun showRewarded(activity: Activity, placement: AdPlacement) =
        RewardResult.NotAvailable

    override fun showPrivacyOptions(activity: Activity) = Unit
}

/**
 * What the ad layer needs to know about the host app, without depending on
 * `:sharedPlayer` or `:core`. `:app` provides the implementation.
 *
 * All methods are called on the main thread.
 */
interface AdsHostSignals {
    /** Audio is playing, buffering, or the user has just pressed Play. */
    fun isPlaybackActive(): Boolean

    /** The full-screen Player is the visible destination. */
    fun isOnPlayerScreen(): Boolean

    /** Onboarding and favourite-category selection are done. */
    fun isOnboardingCompleted(): Boolean

    /** Elapsed-realtime millis when audio last played, or 0 when it never did this process. */
    fun lastPlaybackAt(): Long = 0L
}

/** Renders banner / native slots. `:ads-admob` provides the real one. */
interface AdRenderer {
    @Composable
    fun Banner(placement: AdPlacement, slotKey: String, modifier: Modifier)

    @Composable
    fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier)
}

object NoOpAdRenderer : AdRenderer {
    @Composable
    override fun Banner(placement: AdPlacement, slotKey: String, modifier: Modifier) = Unit

    @Composable
    override fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier) = Unit
}

val LocalAdRenderer = staticCompositionLocalOf<AdRenderer> { NoOpAdRenderer }
val LocalAdsController = staticCompositionLocalOf<AdsController> { NoOpAdsController }

/**
 * Banner placement. Renders nothing (and takes no space) unless an ad renderer
 * is provided, the placement is enabled and an ad actually loaded.
 */
@Composable
fun AdBannerSlot(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    slotKey: String = "0",
) {
    LocalAdRenderer.current.Banner(placement, slotKey, modifier)
}

/** Native / in-feed placement. Same zero-size-when-empty contract as [AdBannerSlot]. */
@Composable
fun AdNativeSlot(
    placement: AdPlacement,
    modifier: Modifier = Modifier,
    slotKey: String = "0",
) {
    LocalAdRenderer.current.Native(placement, slotKey, modifier)
}
