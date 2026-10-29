package tss.t.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsSettingsTest {
    private val defaults = AdsSettings.defaults(debug = false)

    private fun parse(values: Map<String, String>, allowUnitIds: Boolean = true) =
        AdsSettings.parse(defaults, allowUnitIds) { values[it] }

    @Test
    fun `unset keys keep the in-app defaults instead of false or zero`() {
        // Firebase returns "" for a key that was never set.
        val s = AdsSettings.parse(defaults, true) { "" }
        assertEquals(defaults, s)
    }

    @Test
    fun `values override defaults`() {
        val s = parse(
            mapOf(
                AdsKeys.ENABLED to "true",
                AdsKeys.APP_OPEN_DAILY_CAP to "5",
                AdsKeys.NATIVE_TEMPLATE to "medium",
                AdsKeys.PLACEMENTS_ENABLED to """{"app_open_warm":true,"favourite_top":false,"bogus":true}""",
            )
        )
        assertTrue(s.adsEnabled)
        assertEquals(5, s.appOpenDailyCap)
        assertEquals(NativeTemplate.Medium, s.nativeTemplate)
        assertTrue(s.isEnabled(AdPlacement.AppOpenWarm))
        assertFalse(s.isEnabled(AdPlacement.FavouriteTop))
        // Untouched placements keep their default.
        assertTrue(s.isEnabled(AdPlacement.PodcastDetailHeader))
    }

    @Test
    fun `garbage falls back to defaults`() {
        val s = parse(
            mapOf(
                AdsKeys.ENABLED to "yes please",
                AdsKeys.APP_OPEN_DAILY_CAP to "-3",
                AdsKeys.PLACEMENTS_ENABLED to "{not json",
                AdsKeys.NATIVE_INTERVAL to "0",
            )
        )
        assertEquals(defaults.adsEnabled, s.adsEnabled)
        assertEquals(defaults.appOpenDailyCap, s.appOpenDailyCap)
        assertEquals(defaults.placementsEnabled, s.placementsEnabled)
        assertEquals(1, s.nativeInterval)
    }

    @Test
    fun `unit id overrides are validated and ignored in debug`() {
        val json = """{"favourite_top":"ca-app-pub-1234567890123456/1234567890","search_results":"oops"}"""
        val s = parse(mapOf(AdsKeys.UNIT_IDS to json))
        assertEquals(
            mapOf(AdPlacement.FavouriteTop to "ca-app-pub-1234567890123456/1234567890"),
            s.unitIdOverrides
        )
        val debug = parse(mapOf(AdsKeys.UNIT_IDS to json), allowUnitIds = false)
        assertTrue(debug.unitIdOverrides.isEmpty())
    }

    @Test
    fun `debug defaults enable test ads and app open without grace`() {
        val debug = AdsSettings.defaults(debug = true)
        assertTrue(debug.adsEnabled)
        assertTrue(debug.isEnabled(AdPlacement.AppOpenWarm))
        assertFalse(debug.isEnabled(AdPlacement.InterstitialNav))
        assertFalse(debug.isEnabled(AdPlacement.RewardedAdFree))
        assertEquals(0L, debug.gracePeriodHours)
    }
}
