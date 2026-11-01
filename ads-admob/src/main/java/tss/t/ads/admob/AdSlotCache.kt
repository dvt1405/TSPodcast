package tss.t.ads.admob

import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tss.t.ads.AdPlacement
import tss.t.ads.AdsAnalytics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loaded banner / native ads, keyed by placement + slot.
 *
 * Inline slots live in LazyColumns, so their composables are disposed every
 * time they scroll off screen. The AppLovin version created and destroyed an
 * ad view each time, i.e. a new ad request per scroll. Here the loaded ad
 * outlives the composable and is re-attached when the slot comes back.
 *
 * Threading: the Next-Gen SDK delivers load callbacks on a background thread.
 * They only write to a [MutableStateFlow] (thread-safe) and to atomics; the
 * views are touched by Compose on the main thread.
 */
@Singleton
class AdSlotCache @Inject constructor(
    private val analytics: AdsAnalytics,
) {
    class Slot<T : Ad> internal constructor(val key: String) {
        internal val state = MutableStateFlow<T?>(null)
        val ad: StateFlow<T?> = state.asStateFlow()
        internal val loading = AtomicBoolean(false)

        @Volatile
        internal var loadedAt = 0L

        @Volatile
        internal var failedAt = 0L
    }

    private val banners = ConcurrentHashMap<String, Slot<BannerAd>>()
    private val natives = ConcurrentHashMap<String, Slot<NativeAd>>()

    fun bannerSlot(placement: AdPlacement, slotKey: String): Slot<BannerAd> =
        banners.getOrPut("${placement.key}/$slotKey") { Slot("${placement.key}/$slotKey") }

    fun nativeSlot(placement: AdPlacement, slotKey: String): Slot<NativeAd> =
        natives.getOrPut("${placement.key}/$slotKey") { Slot("${placement.key}/$slotKey") }

    /** Loads once per slot. Banner refresh is configured on the AdMob ad unit. */
    fun loadBanner(slot: Slot<BannerAd>, placement: AdPlacement, request: () -> BannerAdRequest) {
        if (slot.state.value != null || !canRetry(slot)) return
        if (!slot.loading.compareAndSet(false, true)) return
        analytics.request(placement)
        val startedAt = System.currentTimeMillis()
        BannerAd.load(request(), object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                analytics.loaded(placement, System.currentTimeMillis() - startedAt, ad.adSourceName())
                ad.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdImpression() = analytics.impression(placement)
                    override fun onAdClicked() = analytics.click(placement)
                    override fun onAdPaid(value: AdValue) = paid(placement, value, ad)
                }
                slot.loadedAt = System.currentTimeMillis()
                slot.state.value = ad
                slot.loading.set(false)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                slot.failedAt = System.currentTimeMillis()
                slot.loading.set(false)
                analytics.loadFailed(
                    placement, adError.code.name, adError.code.value,
                    System.currentTimeMillis() - startedAt,
                )
            }
        })
    }

    /**
     * Loads when the slot is empty, or replaces an ad older than
     * [NATIVE_MAX_AGE_MS] (Google: native ads should be shown within an hour).
     */
    fun loadNative(slot: Slot<NativeAd>, placement: AdPlacement, request: () -> NativeAdRequest) {
        val current = slot.state.value
        val stale = current != null &&
                System.currentTimeMillis() - slot.loadedAt > NATIVE_MAX_AGE_MS
        if ((current != null && !stale) || !canRetry(slot)) return
        if (!slot.loading.compareAndSet(false, true)) return
        analytics.request(placement)
        val startedAt = System.currentTimeMillis()
        NativeAdLoader.load(request(), object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                analytics.loaded(placement, System.currentTimeMillis() - startedAt, nativeAd.adSourceName())
                nativeAd.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdImpression() = analytics.impression(placement)
                    override fun onAdClicked() = analytics.click(placement)
                    override fun onAdPaid(value: AdValue) = paid(placement, value, nativeAd)
                }
                slot.loadedAt = System.currentTimeMillis()
                val previous = slot.state.value
                slot.state.value = nativeAd
                slot.loading.set(false)
                // The old ad is rebound away from by the view on the next
                // composition; destroying it here is safe because only the
                // StateFlow referenced it.
                previous?.destroy()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                slot.failedAt = System.currentTimeMillis()
                slot.loading.set(false)
                analytics.loadFailed(
                    placement, adError.code.name, adError.code.value,
                    System.currentTimeMillis() - startedAt,
                )
            }
        })
    }

    /**
     * Banner views are created against the host Activity
     * (`BannerAd.getView(activity)`), so they must not outlive it. Called when
     * the host Activity is destroyed; the slots reload on the next composition.
     */
    fun clearBanners() {
        val all = banners.values.toList()
        banners.clear()
        all.forEach { slot -> slot.state.value?.destroy(); slot.state.value = null }
    }

    private fun canRetry(slot: Slot<*>): Boolean =
        System.currentTimeMillis() - slot.failedAt > RETRY_AFTER_FAILURE_MS

    private fun paid(placement: AdPlacement, value: AdValue, ad: Ad) = analytics.paid(
        placement, value.valueMicros, value.currencyCode, value.precisionType.name, ad.adSourceName()
    )

    private companion object {
        /** No automatic retry loop (the AppLovin one caused an ANR); one retry per minute at most. */
        const val RETRY_AFTER_FAILURE_MS = 60_000L
        const val NATIVE_MAX_AGE_MS = 55 * 60_000L
    }
}

/** Name of the network that filled [this] ad (GA4 `ad_source`), e.g. "AdMob Network". */
internal fun Ad.adSourceName(): String? =
    runCatching { getResponseInfo().loadedAdSourceResponseInfo?.name }.getOrNull()?.takeIf { it.isNotBlank() }
