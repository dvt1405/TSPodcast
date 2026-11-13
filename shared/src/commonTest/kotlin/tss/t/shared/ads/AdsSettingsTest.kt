package tss.t.shared.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of Android `AdsSettingsTest`, plus the iOS unit ids and `ads_min_app_version`. */
class AdsSettingsTest {
    private val defaults = AdsSettings.defaults(debug = false)

    private fun parse(values: Map<String, String>, allowUnitIds: Boolean = true) =
        AdsSettings.parse(defaults, allowUnitIds) { values[it] }

    @Test
    fun unsetKeysKeepTheInAppDefaultsInsteadOfFalseOrZero() {
        assertEquals(defaults, AdsSettings.parse(defaults, true) { "" })
        assertEquals(defaults, AdsSettings.parse(defaults, true) { null })
    }

    @Test
    fun valuesOverrideDefaults() {
        val s = parse(
            mapOf(
                AdsKeys.ENABLED to "true",
                AdsKeys.APP_OPEN_DAILY_CAP to "5",
                AdsKeys.NATIVE_TEMPLATE to "medium",
                AdsKeys.GRACE_PERIOD_HOURS to "12.0",
                AdsKeys.GA4_AD_IMPRESSION to "false",
                AdsKeys.PLACEMENTS_ENABLED to """{"app_open_warm":true,"favourite_top":false,"bogus":true,"search_results":"false"}""",
            )
        )
        assertTrue(s.adsEnabled)
        assertEquals(5, s.appOpenDailyCap)
        assertEquals(12L, s.gracePeriodHours)
        assertEquals(NativeTemplate.Medium, s.nativeTemplate)
        assertFalse(s.ga4AdImpression)
        assertTrue(s.isEnabled(AdPlacement.AppOpenWarm))
        assertFalse(s.isEnabled(AdPlacement.FavouriteTop))
        assertTrue(s.isEnabled(AdPlacement.PodcastDetailHeader))
        // A string is not a boolean: the default stays.
        assertTrue(s.isEnabled(AdPlacement.SearchResults))
    }

    @Test
    fun garbageFallsBackToDefaults() {
        val s = parse(
            mapOf(
                AdsKeys.ENABLED to "yes please",
                AdsKeys.APP_OPEN_DAILY_CAP to "-3",
                AdsKeys.PLACEMENTS_ENABLED to "{not json",
                AdsKeys.UNIT_IDS to "[1,2]",
                AdsKeys.NATIVE_INTERVAL to "0",
                AdsKeys.NATIVE_FIRST_INDEX to "abc",
            )
        )
        assertEquals(defaults.adsEnabled, s.adsEnabled)
        assertEquals(defaults.appOpenDailyCap, s.appOpenDailyCap)
        assertEquals(defaults.placementsEnabled, s.placementsEnabled)
        assertTrue(s.unitIdOverrides.isEmpty())
        assertEquals(1, s.nativeInterval)
        assertEquals(defaults.nativeFirstIndex, s.nativeFirstIndex)
    }

    @Test
    fun killSwitch() {
        assertFalse(parse(mapOf(AdsKeys.ENABLED to "false")).adsEnabled)
        assertFalse(parse(mapOf(AdsKeys.ENABLED to "0")).isEnabled(AdPlacement.FavouriteTop))
    }

    @Test
    fun unitIdOverridesAreValidatedAndIgnoredInDebug() {
        val json = """{"favourite_top":"ca-app-pub-1234567890123456/1234567890","search_results":"oops","rewarded_interstitial":"ca-app-pub-1234567890123456/1234567890"}"""
        val s = parse(mapOf(AdsKeys.UNIT_IDS to json))
        assertEquals(mapOf(AdPlacement.FavouriteTop to "ca-app-pub-1234567890123456/1234567890"), s.unitIdOverrides)
        assertTrue(parse(mapOf(AdsKeys.UNIT_IDS to json), allowUnitIds = false).unitIdOverrides.isEmpty())
    }

    @Test
    fun debugDefaultsEnableTestAdsAndAppOpenWithoutGrace() {
        val debug = AdsSettings.defaults(debug = true)
        assertTrue(debug.adsEnabled)
        assertTrue(debug.isEnabled(AdPlacement.AppOpenWarm))
        assertFalse(debug.isEnabled(AdPlacement.InterstitialNav))
        assertFalse(debug.isEnabled(AdPlacement.RewardedAdFree))
        assertEquals(0L, debug.gracePeriodHours)
        assertEquals(0, debug.appOpenSkipFirstSessions)
    }

    @Test
    fun minAppVersionTurnsAdsOffOnOlderBuilds() {
        val s = parse(mapOf(AdsKeys.MIN_APP_VERSION to "5"))
        assertFalse(s.forBuild(4).adsEnabled)
        assertTrue(s.forBuild(5).adsEnabled)
        assertTrue(defaults.forBuild(1).adsEnabled)
    }

    @Test
    fun debugAlwaysUsesGoogleTestUnitsAndReleaseTheRealOnes() {
        val overridden = parse(mapOf(AdsKeys.UNIT_IDS to """{"favourite_top":"ca-app-pub-1234567890123456/1234567890"}"""))
        for (placement in AdPlacement.entries) {
            val test = IosAdUnitIds.forPlacement(placement, overridden, debug = true)
            assertTrue(test.startsWith("ca-app-pub-3940256099942544/"), "${placement.key}: $test")
        }
        assertEquals("ca-app-pub-1234567890123456/1234567890", IosAdUnitIds.forPlacement(AdPlacement.FavouriteTop, overridden, debug = false))
        assertEquals("ca-app-pub-8696388065064767/5141512895", IosAdUnitIds.forPlacement(AdPlacement.SearchResults, overridden, debug = false))
        assertEquals("ca-app-pub-8696388065064767/2538245333", IosAdUnitIds.forPlacement(AdPlacement.DiscoverInline, defaults, debug = false))
    }

    @Test
    fun placementKeysAreUnique() {
        assertEquals(AdPlacement.entries.size, AdPlacement.entries.map { it.key }.toSet().size)
        assertEquals(AdPlacement.InterstitialNav, AdPlacement.fromKey("interstitial_nav"))
    }
}
