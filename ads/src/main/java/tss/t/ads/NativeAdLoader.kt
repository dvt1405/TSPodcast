package tss.t.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdRevenueListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import tss.t.securedtoken.NativeLib
import tss.t.sharedfirebase.TSAnalytics
import tss.t.sharedlibrary.utils.LocalRemoteConfig
import tss.t.sharedlibrary.utils.drawDivider

enum class NativeAd {
    Small,
    Medium,
    Manual
}

/**
 * Ad loader to load Max Native ads with Templates API using Jetpack Compose.
 */
class MaxTemplateNativeAdViewComposableLoader(
    adUnitIdentifier: String = NativeLib.getNativeMediumId(),
    context: Context,
    val format: NativeAd = NativeAd.Medium
) {
    var nativeAdView by mutableStateOf<MaxNativeAdView?>(null)
    private var nativeAd: MaxAd? = null
    private var nativeAdLoader: MaxNativeAdLoader = MaxNativeAdLoader(adUnitIdentifier, context)
    private var isLoaded = false

    init {
        setupListener()
    }

    private fun setupListener() {
        val adRevenueListener = MaxAdRevenueListener { }
        val adListener = object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(loadedNativeAdView: MaxNativeAdView?, ad: MaxAd) {
                if (nativeAd != null) {
                    nativeAdLoader.destroy(nativeAd)
                    runCatching {
                        nativeAdView?.removeAllViews()
                        nativeAdView = null
                    }.onFailure {
                        Firebase.crashlytics.recordException(it)
                    }
                }

                nativeAd = ad // Save ad for cleanup.
                runCatching {
                    nativeAdView = loadedNativeAdView
                }.onFailure {
                    Firebase.crashlytics.recordException(it)
                }
                isLoaded = true
                TSAnalytics.instance?.trackEvent(
                    eventName = AdsConstants.EVENT_NAME_AD_NATIVE_LOADED,
                    prop = ad.toArray()
                )
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                isLoaded = false
                TSAnalytics.instance?.trackEvent(
                    eventName = AdsConstants.EVENT_NAME_AD_NATIVE_LOAD_FAILED,
                    screenName = TSAnalytics.instance?.currentScreenName,
                    *error.toArray(),
                    "adUnitId" to adUnitId
                )
            }

            override fun onNativeAdClicked(ad: MaxAd) {
                TSAnalytics.instance?.trackEvent(
                    eventName = AdsConstants.EVENT_NAME_AD_NATIVE_CLICKED,
                    screenName = TSAnalytics.instance?.currentScreenName,
                    *ad.toArray()
                )
            }

            override fun onNativeAdExpired(nativeAd: MaxAd) {
                isLoaded = false
                nativeAdLoader.destroy(nativeAd)
                TSAnalytics.instance?.trackEvent(
                    eventName = AdsConstants.EVENT_NAME_AD_NATIVE_EXPIRED,
                    screenName = TSAnalytics.instance?.currentScreenName,
                    *nativeAd.toArray()
                )
            }
        }
        nativeAdLoader.apply {
            setRevenueListener(adRevenueListener)
            setNativeAdListener(adListener)
        }
    }

    fun destroy() {
        // Must destroy native ad or else there will be memory leaks.
        if (nativeAd != null) {
            // Call destroy on the native ad from any native ad loader.
            nativeAdLoader.destroy(nativeAd)
        }

        // Destroy the actual loader itself
        nativeAdLoader.destroy()
        isLoaded = false
    }

    fun loadAd() {
        if (!isLoaded) {
            nativeAdLoader.loadAd()
        }
    }
}

/**
 * Jetpack Compose function to display MAX native ads using the Templates API.
 */
@Composable
fun MaxTemplateNativeAdViewComposable(nativeAdLoader: MaxTemplateNativeAdViewComposableLoader) {
    val configAPI = LocalRemoteConfig.current
    val adEnable = remember {
        configAPI.getBoolean(AdsConstants.KEY_ADS_ENABLE)
    }
    if (!adEnable) return
    LaunchedEffect(Unit) {
        nativeAdLoader.loadAd()
    }

    nativeAdLoader.nativeAdView?.let { adView ->
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .background(Color.White)
                .padding(vertical = 16.dp)
                .height(
                    if (nativeAdLoader.format == NativeAd.Medium) {
                        300.dp
                    } else {
                        150.dp
                    }
                )
                .fillMaxWidth()
                .drawDivider(horizontalPaddingDp = 16.dp)
        )
    }
}