package tss.t.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tss.t.sharedlibrary.analytics.RecordingAnalyticsTracker

class AdsAnalyticsTest {

    private fun analytics(ga4AdImpression: Boolean = true): Pair<FirebaseAdsAnalytics, RecordingAnalyticsTracker> {
        val tracker = RecordingAnalyticsTracker()
        val settings = AdsSettings.defaults(debug = false).copy(ga4AdImpression = ga4AdImpression)
        val config = object : AdsConfig {
            override fun current() = settings
        }
        return FirebaseAdsAnalytics(tracker, config) to tracker
    }

    @Test
    fun `micros become currency units`() {
        assertEquals(1.5, AdRevenue.valueFromMicros(1_500_000), 0.0)
        assertEquals(0.000123, AdRevenue.valueFromMicros(123), 1e-12)
        assertEquals(0.0, AdRevenue.valueFromMicros(0), 0.0)
    }

    @Test
    fun `paid event becomes GA4 ad_impression with value and currency`() {
        val (ads, tracker) = analytics()
        tracker.screen("discover")
        ads.paid(AdPlacement.DiscoverInline, 2_340_000, "usd", "PRECISE", "AdMob Network")

        val impression = tracker.events.single { it.name == "ad_impression" }
        assertEquals("AdMob", impression.params["ad_platform"])
        assertEquals("AdMob Network", impression.params["ad_source"])
        assertEquals("native", impression.params["ad_format"])
        assertEquals("discover_inline", impression.params["ad_unit_name"])
        assertEquals(2.34, impression.params["value"] as Double, 1e-9)
        assertEquals("USD", impression.params["currency"])
        assertEquals("precise", impression.params["precision"])
        assertEquals("discover", impression.params["screen"])

        val paid = tracker.events.single { it.name == "ads_paid" }
        assertEquals(2_340_000L, paid.params["value_micros"])
    }

    @Test
    fun `no currency means no value, GA4 would reject it`() {
        val e = AdRevenue.ga4AdImpression(AdPlacement.AppOpenWarm, 1000, "", "ESTIMATED", null, null)
        assertFalse(e.params.containsKey("value"))
        assertFalse(e.params.containsKey("currency"))
        assertEquals("app_open", e.params["ad_format"])
        assertEquals("AdMob", e.params["ad_source"])
    }

    @Test
    fun `remote config switch turns ad_impression off but keeps ads_paid`() {
        val (ads, tracker) = analytics(ga4AdImpression = false)
        ads.paid(AdPlacement.InterstitialNav, 10, "VND", "ESTIMATED", null)
        assertEquals(listOf("ads_paid"), tracker.names())
    }

    @Test
    fun `funnel events carry placement and GA4 format`() {
        val (ads, tracker) = analytics()
        ads.request(AdPlacement.FavouriteTop)
        ads.loaded(AdPlacement.FavouriteTop, 420, "AdMob Network")
        ads.loadFailed(AdPlacement.SearchResults, "NO_FILL", 3, 900)
        ads.showFailed(AdPlacement.RewardedAdFree, "AD_REUSED")
        ads.dismiss(AdPlacement.AppOpenWarm)
        assertEquals(
            listOf("ads_request", "ads_loaded", "ads_load_failed", "ads_show_failed", "ads_dismiss"),
            tracker.names()
        )
        val loaded = tracker.events[1]
        assertEquals("favourite_top", loaded.params["placement"])
        assertEquals("banner", loaded.params["ad_format"])
        assertEquals(420L, loaded.params["latency_ms"])
        val failed = tracker.events[2]
        assertEquals("NO_FILL", failed.params["code"])
        assertEquals(3L, failed.params["code_num"])
        assertEquals("rewarded", tracker.events[3].params["ad_format"])
    }

    @Test
    fun `every GA4 ad format name is known`() {
        AdFormat.entries.forEach { assertTrue(AdRevenue.formatName(it).isNotBlank()) }
        assertEquals("banner", AdRevenue.formatName(AdFormat.CollapsibleBanner))
    }

    @Test
    fun `ga4 switch is read from remote config`() {
        val defaults = AdsSettings.defaults(debug = false)
        assertTrue(defaults.ga4AdImpression)
        val off = AdsSettings.parse(defaults, true) { if (it == AdsKeys.GA4_AD_IMPRESSION) "false" else null }
        assertFalse(off.ga4AdImpression)
    }
}
