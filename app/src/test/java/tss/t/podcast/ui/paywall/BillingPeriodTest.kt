package tss.t.podcast.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tss.t.podcast.ui.screens.paywall.BillingPeriod

class BillingPeriodTest {
    @Test
    fun parsesPlayPeriods() {
        assertEquals(BillingPeriod(1, BillingPeriod.Unit.Month), BillingPeriod.parse("P1M"))
        assertEquals(BillingPeriod(1, BillingPeriod.Unit.Year), BillingPeriod.parse("P1Y"))
        assertEquals(BillingPeriod(3, BillingPeriod.Unit.Month), BillingPeriod.parse("P3M"))
        assertEquals(BillingPeriod(1, BillingPeriod.Unit.Week), BillingPeriod.parse("P1W"))
        assertEquals(BillingPeriod(7, BillingPeriod.Unit.Day), BillingPeriod.parse(" p7d "))
    }

    @Test
    fun unsupportedPeriodsAreNull() {
        assertNull(BillingPeriod.parse(null))
        assertNull(BillingPeriod.parse(""))
        assertNull(BillingPeriod.parse("P0M"))
        assertNull(BillingPeriod.parse("P1Y6M"))
        assertNull(BillingPeriod.parse("PT1H"))
    }
}
