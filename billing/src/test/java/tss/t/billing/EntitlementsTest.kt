package tss.t.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private val settings = IapSettings.DEFAULTS

    private fun purchase(vararg ids: String, state: OwnedState = OwnedState.Purchased, ack: Boolean = true) =
        OwnedPurchase(ids.toList(), state, ack)

    @Test
    fun `nothing owned is not premium`() {
        val e = Entitlements.compute(emptyList(), settings)
        assertFalse(e.premium)
        assertTrue(e.verified)
        assertNull(e.subscriptionStoreProductId)
    }

    @Test
    fun `purchased subscription or lifetime is premium, acknowledged or not`() {
        val sub = Entitlements.compute(listOf(purchase("premium", ack = false)), settings)
        assertTrue(sub.premium)
        assertEquals("premium", sub.subscriptionStoreProductId)

        val lifetime = Entitlements.compute(listOf(purchase("remove_ads_lifetime")), settings)
        assertTrue(lifetime.premium)
        assertNull(lifetime.subscriptionStoreProductId)
    }

    @Test
    fun `pending is shown but never granted`() {
        val e = Entitlements.compute(listOf(purchase("remove_ads_lifetime", state = OwnedState.Pending)), settings)
        assertFalse(e.premium)
        assertEquals(setOf("remove_ads_lifetime"), e.pendingProductIds)
    }

    @Test
    fun `unknown products and states grant nothing`() {
        val e = Entitlements.compute(
            listOf(purchase("some_other_product"), purchase("premium", state = OwnedState.Unknown)),
            settings,
        )
        assertFalse(e.premium)
    }

    @Test
    fun `a product dropped from remote config still grants what was bought`() {
        val onlyMonthly = settings.copy(products = settings.products.take(1))
        assertTrue(Entitlements.compute(listOf(purchase("remove_ads_lifetime")), onlyMonthly).premium)
    }

    @Test
    fun `products without ad_free do not make premium`() {
        val other = IapSettings(
            enabled = true,
            defaultProductId = "tip",
            products = listOf(
                IapProduct("tip", IapProductType.OneTime, "tip_jar", null, null, emptyList(), null, 1)
            ),
        )
        assertFalse(Entitlements.compute(listOf(purchase("tip_jar")), other).premium)
    }

    @Test
    fun `internal id uses the launched product to tell base plans apart`() {
        assertEquals("premium_yearly", Entitlements.internalId("premium", settings, "premium_yearly"))
        // Two base plans share "premium": unknown without the launched product.
        assertEquals("premium", Entitlements.internalId("premium", settings))
        assertEquals("remove_ads_lifetime", Entitlements.internalId("remove_ads_lifetime", settings))
        // Launched product of another store product is ignored.
        assertEquals("remove_ads_lifetime", Entitlements.internalId("remove_ads_lifetime", settings, "premium_yearly"))
    }

    private fun phase(micros: Long, period: String) = PricingPhaseInfo(micros, if (micros == 0L) "Free" else "$micros", period)

    @Test
    fun `offer prefers an eligible free trial of the base plan`() {
        val base = SubscriptionOfferInfo("yearly", null, "t-base", listOf(phase(990_000_000, "P1Y")))
        val trial = SubscriptionOfferInfo("yearly", "trial7", "t-trial", listOf(phase(0, "P1W"), phase(990_000_000, "P1Y")))
        val intro = SubscriptionOfferInfo("yearly", "intro", "t-intro", listOf(phase(490_000_000, "P1Y"), phase(990_000_000, "P1Y")))
        val monthly = SubscriptionOfferInfo("monthly", null, "t-month", listOf(phase(99_000_000, "P1M")))
        val all = listOf(monthly, intro, base, trial)

        val yearly = Entitlements.pickOffer(all, "yearly")
        assertEquals("t-trial", yearly?.offerToken)
        assertEquals("P1W", yearly?.freeTrialPeriod)
        assertEquals("P1Y", yearly?.recurringPhase?.billingPeriod)

        // Not eligible for the trial (Play leaves it out): the base plan, not a paid intro.
        assertEquals("t-base", Entitlements.pickOffer(listOf(monthly, intro, base), "yearly")?.offerToken)
        assertNull(Entitlements.pickOffer(listOf(monthly, intro, base), "yearly")?.freeTrialPeriod)
        assertEquals("t-month", Entitlements.pickOffer(all, "monthly")?.offerToken)
        assertNull(Entitlements.pickOffer(all, "weekly"))
    }
}
