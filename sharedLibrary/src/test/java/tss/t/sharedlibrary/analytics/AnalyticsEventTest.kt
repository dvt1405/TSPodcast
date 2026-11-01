package tss.t.sharedlibrary.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {

    @Test
    fun `names become GA4 snake_case`() {
        assertEquals("podcast_open", AnalyticsEvent.sanitizeName("Podcast Open"))
        assertEquals("ads_load_failed", AnalyticsEvent.sanitizeName("ads-load.failed"))
        assertEquals("fcm_null_message_receive", AnalyticsEvent.sanitizeName("fcm_null_message_receive"))
        // Must start with a letter; symbols and accents are dropped.
        assertEquals("abc", AnalyticsEvent.sanitizeName("_1abc!"))
        assertEquals("unnamed", AnalyticsEvent.sanitizeName("!!!"))
    }

    @Test
    fun `names are cut to 40 characters`() {
        val name = AnalyticsEvent.sanitizeName("a".repeat(60))
        assertEquals(40, name.length)
    }

    @Test
    fun `values are normalised to string, long or double and null is dropped`() {
        val e = AnalyticsEvent.of(
            "x",
            "int" to 3,
            "long" to 4L,
            "float" to 1.5f,
            "bool" to true,
            "nan" to Double.NaN,
            "none" to null,
            "enum" to Thread.State.RUNNABLE,
        )
        assertEquals(3L, e.params["int"])
        assertEquals(4L, e.params["long"])
        assertEquals(1.5, e.params["float"])
        assertEquals("true", e.params["bool"])
        assertEquals("runnable", e.params["enum"])
        assertFalse(e.params.containsKey("nan"))
        assertFalse(e.params.containsKey("none"))
    }

    @Test
    fun `string values are cut to 100 characters without splitting an emoji`() {
        val long = "x".repeat(99) + "🎧" // 99 + headphones (2 chars)
        val cut = AnalyticsEvent.trimValue(long)
        assertEquals(99, cut.length)
        assertTrue(AnalyticsEvent.trimValue("y".repeat(150)).length == 100)
    }

    @Test
    fun `at most 25 parameters are kept, the first ones`() {
        val params = (1..30).map { "p$it" to it }
        val e = AnalyticsEvent.of("many", params)
        assertEquals(25, e.params.size)
        assertEquals(1L, e.params["p1"])
        assertNull(e.params["p26"])
    }

    @Test
    fun `throttle allows each key once and caps the total`() {
        val t = EventThrottle(perKey = 1, total = 2)
        assertTrue(t.allow("a"))
        assertFalse(t.allow("a"))
        assertTrue(t.allow("b"))
        assertFalse(t.allow("c"))
    }
}
