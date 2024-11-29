package tss.t.ads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.ads.databinding.NativeAdsContainerBinding
import tss.t.securedtoken.NativeLib
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeAdsManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) : MaxNativeAdListener() {
    init {
        instance = this
    }

    private fun createNativeAdView(): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.native_ads_template)
            .setTitleTextViewId(R.id.title_text_view)
            .setBodyTextViewId(R.id.body_text_view)
            .setAdvertiserTextViewId(R.id.advertiser_textView)
            .setIconImageViewId(R.id.icon_image_view)
            .setOptionsContentViewGroupId(R.id.options_view)
            .setCallToActionButtonId(R.id.cta_button)
            .build()
        return MaxNativeAdView(binder, context)
    }

    private fun createNativeAdView(view: View): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(view)
            .build()
        return MaxNativeAdView(binder, context)
    }

    @Composable
    fun NativeCompose(
        modifier: Modifier,
        adLoader: MaxNativeAdLoader = remember {
            MaxNativeAdLoader(
                NativeLib.getNativeId(),
                context
            )
        }
    ) {
        val context = LocalContext.current
        val containerView = remember {
            NativeAdsContainerBinding
                .inflate(LayoutInflater.from(context))
                .root
        }
        var visibility by remember {
            mutableIntStateOf(View.GONE)
        }

        LifecycleEventEffect(Lifecycle.Event.ON_CREATE) {
            adLoader.setNativeAdListener(object : MaxNativeAdListener() {
                override fun onNativeAdLoaded(adView: MaxNativeAdView?, maxAd: MaxAd) {
                    super.onNativeAdLoaded(adView, maxAd)
                    visibility = View.VISIBLE
                    adView ?: return
                    containerView.removeAllViews()
                    containerView.addView(adView)
                }

                override fun onNativeAdLoadFailed(p0: String, p1: MaxError) {
                    super.onNativeAdLoadFailed(p0, p1)
                    visibility = View.GONE
                }
            })
            adLoader.loadAd(createNativeAdView())
        }

        AndroidView(
            factory = {
                containerView
            },
            modifier = modifier,
            onReset = {
                it.visibility = visibility
            },
            update = {
                it.visibility = visibility
            },
        )
    }

    override fun onNativeAdClicked(p0: MaxAd) {
        super.onNativeAdClicked(p0)
    }

    override fun onNativeAdExpired(p0: MaxAd) {
        super.onNativeAdExpired(p0)
    }

    override fun onNativeAdLoadFailed(p0: String, p1: MaxError) {
        super.onNativeAdLoadFailed(p0, p1)
    }

    override fun onNativeAdLoaded(p0: MaxNativeAdView?, p1: MaxAd) {
        super.onNativeAdLoaded(p0, p1)
    }

    companion object {
        var instance: NativeAdsManager? = null
            private set
    }
}