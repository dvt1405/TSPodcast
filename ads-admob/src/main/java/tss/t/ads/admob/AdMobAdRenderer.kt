package tss.t.ads.admob

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import tss.t.ads.AdFormat
import tss.t.ads.AdPlacement
import tss.t.ads.AdPolicy
import tss.t.ads.AdRenderer
import tss.t.ads.AdsConfig
import tss.t.ads.Decision
import tss.t.ads.NativeTemplate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compose renderer for banner and native slots.
 *
 * A slot renders nothing (zero height) until consent allows requests, the SDK
 * is initialised, the placement is enabled and an ad has actually loaded; a
 * no-fill never leaves a gap in the layout.
 */
@Singleton
class AdMobAdRenderer @Inject constructor(
    private val controller: AdMobAdsController,
    private val config: AdsConfig,
    private val policy: AdPolicy,
    private val unitIds: AdUnitIds,
    private val cache: AdSlotCache,
) : AdRenderer {

    @Composable
    override fun Banner(placement: AdPlacement, slotKey: String, modifier: Modifier) {
        val canLoad by controller.canLoadAds.collectAsState()
        if (!canLoad) return
        val settings = remember(placement) { config.current() }
        if (policy.canShowInline(placement, settings, canLoad) !is Decision.Allow) return
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() } ?: return
        val widthDp = LocalConfiguration.current.screenWidthDp
        val slot = remember(placement, slotKey) { cache.bannerSlot(placement, slotKey) }
        LaunchedEffect(slot) {
            cache.loadBanner(slot, placement) {
                val unitId = unitIds.forPlacement(placement, settings)
                // Inline adaptive, capped so a tall creative cannot take over a feed.
                val size = AdSize.getInlineAdaptiveBannerAdSize(widthDp, MAX_BANNER_HEIGHT_DP)
                BannerAdRequest.Builder(unitId, size).apply {
                    if (placement.format == AdFormat.CollapsibleBanner) {
                        setGoogleExtrasBundle(Bundle().apply { putString("collapsible", "bottom") })
                    }
                }.build()
            }
        }
        val banner by slot.ad.collectAsState()
        val ad = banner ?: return
        AndroidView(
            factory = { ctx -> FrameLayout(ctx) },
            update = { frame ->
                val view = ad.getView(activity)
                if (view.parent !== frame) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    frame.removeAllViews()
                    frame.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_HORIZONTAL
                        )
                    )
                }
            },
            onRelease = { frame -> frame.removeAllViews() },
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }

    @Composable
    override fun Native(placement: AdPlacement, slotKey: String, modifier: Modifier) {
        val canLoad by controller.canLoadAds.collectAsState()
        if (!canLoad) return
        val settings = remember(placement) { config.current() }
        if (policy.canShowInline(placement, settings, canLoad) !is Decision.Allow) return
        val slot = remember(placement, slotKey) { cache.nativeSlot(placement, slotKey) }
        LaunchedEffect(slot) {
            cache.loadNative(slot, placement) {
                NativeAdRequest.Builder(
                    unitIds.forPlacement(placement, settings),
                    listOf(NativeAd.NativeAdType.NATIVE)
                )
                    // Never let a video ad make sound over a podcast.
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build()
            }
        }
        val nativeAd by slot.ad.collectAsState()
        val ad = nativeAd ?: return
        val template = when (placement) {
            AdPlacement.DiscoverInline -> NativeTemplate.Medium
            else -> settings.nativeTemplate
        }
        val colors = NativeAdColors(
            text = MaterialTheme.colorScheme.onSurface.toArgb(),
            secondaryText = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
            background = MaterialTheme.colorScheme.surface.toArgb(),
            accent = MaterialTheme.colorScheme.primary.toArgb(),
            onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
        )
        AndroidView(
            factory = { ctx -> NativeAdTemplateView(ctx, template) },
            update = { view -> view.bind(ad, colors) },
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }

    private companion object {
        const val MAX_BANNER_HEIGHT_DP = 100
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
