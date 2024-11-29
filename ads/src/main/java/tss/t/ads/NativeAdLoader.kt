package tss.t.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import tss.t.securedtoken.NativeLib

/**
 * Ad loader to load Max Native ads with Templates API using Jetpack Compose.
 */
class MaxTemplateNativeAdViewComposableLoader(
    adUnitIdentifier: String = NativeLib.getNativeMediumId(),
    context: Context,
) {
    var nativeAdView = mutableStateOf<MaxNativeAdView?>(null)
    private var nativeAd: MaxAd? = null
    private var nativeAdLoader: MaxNativeAdLoader = MaxNativeAdLoader(adUnitIdentifier, context)
    private var isLoaded = false

    init {
        val adRevenueListener = MaxAdRevenueListener { }
        val adListener = object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(loadedNativeAdView: MaxNativeAdView?, ad: MaxAd) {
                if (nativeAd != null) {
                    nativeAdLoader.destroy(nativeAd)
                    nativeAdView.value?.let {
                        it.removeAllViews()
                        it.addView(loadedNativeAdView)
                    }
                }

                nativeAd = ad // Save ad for cleanup.
                nativeAdView.value = loadedNativeAdView
                isLoaded = true
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                isLoaded = false
            }

            override fun onNativeAdClicked(ad: MaxAd) {
            }

            override fun onNativeAdExpired(nativeAd: MaxAd) {
                isLoaded = false
                nativeAdLoader.destroy(nativeAd)
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

    LaunchedEffect(Unit) {
        nativeAdLoader.loadAd()
    }

    nativeAdLoader.nativeAdView.value?.let { adView ->
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .height(300.dp)
                .fillMaxWidth()
                .background(Color.White)
        )
    }
}