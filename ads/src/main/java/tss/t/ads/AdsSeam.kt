package tss.t.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op advertising seam.
 *
 * The AppLovin MAX SDK was removed along with the `applovin-quality-service`
 * Gradle plugin and its SafeDK bytecode instrumentation. This module is kept,
 * rather than deleted, so that wiring up a different ad network later means
 * implementing one module instead of re-threading call sites back through nine
 * files in `:app`.
 *
 * Everything here is intentionally inert: the composables draw nothing and
 * occupy no space, and the managers do nothing. Nothing in this module may take
 * a dependency on an ad SDK - that is the point of the seam.
 */
@Singleton
class AdsManager @Inject constructor() {

    /** Called from `App.onCreate`. No SDK to initialise. */
    fun initSdk() = Unit

    /** Called from `MainActivity.onResume`. No app-open ad to show. */
    fun loadOpenAds() = Unit
}

/**
 * Kept so the banner CompositionLocal wiring in `MainActivity` survives a future
 * ad network being added back.
 */
@Singleton
class BannerAdsManager @Inject constructor() {
    fun destroy() = Unit
}

val LocalBannerAdsManagerScope = staticCompositionLocalOf<BannerAdsManager?> { null }

/**
 * Banner placement. Renders nothing while no ad network is configured.
 *
 * Kept as a named slot so the layout positions that used to carry ads stay
 * visible in the source; a reviewer can see where inventory would return.
 */
@Composable
fun AdBannerSlot(modifier: Modifier = Modifier) = Unit

/** Native/in-feed placement. Renders nothing. */
@Composable
fun AdNativeSlot(modifier: Modifier = Modifier) = Unit
