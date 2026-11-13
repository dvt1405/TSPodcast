package tss.t.billing

import org.junit.Assert.assertEquals
import org.junit.Test
import tss.t.sharedlibrary.analytics.RecordingAnalyticsTracker

class BillingAnalyticsTest {
    private val tracker = RecordingAnalyticsTracker()
    private val analytics = TrackerBillingAnalytics(tracker)

    @Test
    fun `contract event names and parameters`() {
        analytics.paywallView(PaywallSource.SETTINGS)
        analytics.purchaseStart("premium_yearly", PaywallSource.SETTINGS)
        analytics.purchaseSuccess("premium_yearly", PaywallSource.SETTINGS)
        analytics.purchaseCancel("premium_monthly", PaywallSource.SETTINGS)
        analytics.purchaseFail("remove_ads_lifetime", PaywallSource.SETTINGS, "NETWORK_ERROR")
        analytics.purchaseRestore(PaywallSource.SETTINGS, BillingEvents.RESULT_NONE)

        assertEquals(
            listOf("paywall_view", "purchase_start", "purchase_success", "purchase_cancel", "purchase_fail", "purchase_restore"),
            tracker.names(),
        )
        assertEquals(mapOf("source" to "settings"), tracker.events[0].params)
        assertEquals(mapOf("product_id" to "premium_yearly", "source" to "settings"), tracker.events[1].params)
        assertEquals("NETWORK_ERROR", tracker.events[4].params["code"])
        assertEquals(mapOf("source" to "settings", "result" to "none"), tracker.events[5].params)
    }
}
