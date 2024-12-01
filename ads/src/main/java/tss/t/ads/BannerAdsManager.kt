package tss.t.ads

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxErrorCode
import com.applovin.mediation.ads.MaxAdView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tss.t.securedtoken.NativeLib
import tss.t.sharedfirebase.TSAnalytics
import javax.inject.Inject
import javax.inject.Singleton


data class AdViewHolder(
    val adView: MutableState<MaxAdView>
)

/**
 * Jetpack Compose function used to display MAX banner/leader/MREC ads.
 */
@Composable
fun MaxAdViewComposable(
    adUnitId: String = NativeLib.getAdBannerId(),
    adFormat: MaxAdFormat = MaxAdFormat.BANNER,
    modifier: Modifier = Modifier,
    tsAnalytics: TSAnalytics,
) {
    val context = LocalContext.current

    val adViewModifier = when (adFormat) {
        // Set background or background color for ads to be fully functional.
        MaxAdFormat.BANNER -> modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(androidx.compose.ui.graphics.Color.White)

        MaxAdFormat.LEADER -> modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(androidx.compose.ui.graphics.Color.White)

        MaxAdFormat.MREC -> modifier
            .width(300.dp)
            .height(250.dp)
            .background(androidx.compose.ui.graphics.Color.White)

        else -> modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
    }
    var visibility by remember {
        mutableIntStateOf(View.GONE)
    }
    var failedCount by remember {
        mutableIntStateOf(0)
    }
    val scope = rememberCoroutineScope()
    var startAutoRefresh by remember {
        mutableStateOf(false)
    }
    val listener = remember {
        object : MaxAdViewAdListenerAdapter(tsAnalytics) {
            override fun onAdLoaded(ad: MaxAd) {
                super.onAdLoaded(ad)
                visibility = View.VISIBLE
                failedCount = 0
                startAutoRefresh = true
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                super.onAdDisplayFailed(ad, error)
                visibility = View.GONE
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                super.onAdLoadFailed(adUnitId, error)
                visibility = View.GONE
                failedCount++
                BannerAdsManager.instance?.errorHandle(adUnitId, error, failedCount, scope)
            }
        }
    }

    val adView by remember {
        mutableStateOf(
            MaxAdView(adUnitId, adFormat, context).apply {
                setListener(listener)
                loadAd()
            }
        )
    }

    LaunchedEffect(startAutoRefresh) {
        if (startAutoRefresh) {
            adView.startAutoRefresh()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = adViewModifier
    )

    DisposableEffect(Unit) {
        onDispose {
            adView.destroy()
        }
    }
}

@Singleton
class BannerAdsManager @Inject constructor(
    @ApplicationContext
    private val applicationContext: Context,
    private val tsAnalytics: TSAnalytics,
    private val scope: ApplovinCoroutineScope
) {
    private val showAdThreshHold by lazy {
        AdsConstants.A_MINUTES * 10
    }

    private var lastShowAd: Long = 0L

    init {
        instance = this
    }

    fun errorHandle(
        adUnitId: String,
        error: MaxError,
        failedCount: Int,
        scope: CoroutineScope,
        callback: () -> Unit = {}
    ) {
        if (failedCount > 10) return
        when (error.code) {
            MaxErrorCode.NETWORK_ERROR,
            MaxErrorCode.NO_NETWORK,
            MaxErrorCode.NETWORK_TIMEOUT -> {
                scope.launch {
                    delay(5_000L * failedCount)
                    callback()
                }
            }

            MaxErrorCode.AD_LOAD_FAILED -> {
                scope.launch {
                    delay(5_000L * failedCount)
                    callback()
                }
            }

            MaxErrorCode.INVALID_AD_UNIT_ID -> {

            }

            else -> {
                scope.launch {
                    delay(5_000L * failedCount)
                    callback()
                }
            }
        }
    }

    companion object {
        var instance: BannerAdsManager? = null
            private set
    }
}