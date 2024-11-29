package tss.t.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import tss.t.securedtoken.NativeLib
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
                    runCatching {
                        nativeAdView.value?.removeAllViews()
                        nativeAdView.value = null
                    }.onFailure {
                        Firebase.crashlytics.recordException(it)
                    }
                }

                nativeAd = ad // Save ad for cleanup.
                runCatching {
                    nativeAdView.value = loadedNativeAdView
                }.onFailure {
                    Firebase.crashlytics.recordException(it)
                }
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