package tss.t.ads.admob

import tss.t.ads.AdFormat
import tss.t.ads.AdPlacement
import tss.t.ads.AdsSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ad unit id for a placement: the Remote Config override (`ads_unit_ids`,
 * release builds only) if present, otherwise the per-format id baked in at
 * build time (Google test ids unless ADMOB_UNIT_* Gradle properties are set;
 * see ads-admob/build.gradle.kts).
 */
@Singleton
class AdUnitIds @Inject constructor() {
    fun forPlacement(placement: AdPlacement, settings: AdsSettings): String =
        settings.unitIdOverrides[placement] ?: defaultFor(placement.format)

    private fun defaultFor(format: AdFormat): String = when (format) {
        AdFormat.AppOpen -> BuildConfig.ADMOB_UNIT_APP_OPEN
        AdFormat.Banner, AdFormat.CollapsibleBanner -> BuildConfig.ADMOB_UNIT_BANNER
        AdFormat.Native -> BuildConfig.ADMOB_UNIT_NATIVE
        AdFormat.Interstitial -> BuildConfig.ADMOB_UNIT_INTERSTITIAL
        AdFormat.Rewarded -> BuildConfig.ADMOB_UNIT_REWARDED
    }
}
