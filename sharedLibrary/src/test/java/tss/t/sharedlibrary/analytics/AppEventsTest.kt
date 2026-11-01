package tss.t.sharedlibrary.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEventsTest {

    @Test
    fun `search never sends the text, only its shape`() {
        val e = AppEvents.search("  tin tức sáng  ", "all", 12)
        assertEquals("search", e.name)
        assertEquals(12L, e.params["term_length"])
        assertEquals(3L, e.params["word_count"])
        assertEquals("all", e.params["search_mode"])
        assertEquals(12L, e.params["result_count"])
        assertFalse(e.params.values.any { it.toString().contains("tin") })
    }

    @Test
    fun `speed change and sleep timer keep the iOS parameters`() {
        val speed = AppEvents.speedChange(15, applyToAll = false)
        assertEquals("speed_change", speed.name)
        assertEquals("1.5", speed.params["rate"])
        assertEquals("show", speed.params["scope"])
        assertEquals("all", AppEvents.speedChange(10, true).params["scope"])

        val timer = AppEvents.sleepTimerSet("end_of_episode", "shortcut")
        assertEquals(mapOf("option" to "end_of_episode", "origin" to "shortcut"), timer.params)
    }

    @Test
    fun `podcast locations change says what changed`() {
        val add = AppEvents.podcastLocationsChange(listOf("VN", "US"), listOf("VN"))
        assertEquals("add", add.params["change"])
        assertEquals("VN", add.params["primary_location"])
        assertEquals("VN,US", add.params["locations"])
        assertEquals("false", add.params["primary_changed"])

        val reorder = AppEvents.podcastLocationsChange(listOf("US", "VN"), listOf("VN", "US"))
        assertEquals("reorder", reorder.params["change"])
        assertEquals("true", reorder.params["primary_changed"])
    }

    @Test
    fun `select content carries section and region`() {
        val e = AppEvents.selectContent(AppEvents.ContentType.PODCAST, 920666L, AppEvents.Section.LOCATION, "JP", 2)
        assertEquals("select_content", e.name)
        assertEquals("920666", e.params["item_id"])
        assertEquals("location", e.params["section"])
        assertEquals("JP", e.params["region"])
        assertEquals(2L, e.params["position"])
    }

    @Test
    fun `playback error keeps a short code name`() {
        val item = AppEvents.PlaybackItem(AppEvents.ContentType.EPISODE, "42", 7L)
        val e = AppEvents.playbackError(item, "ERROR_CODE_IO_BAD_HTTP_STATUS", 2004)
        assertEquals("io_bad_http_status", e.params["error_code"])
        assertEquals(2004L, e.params["error_code_num"])
        assertEquals(7L, e.params["feed_id"])
    }

    @Test
    fun `every event name fits GA4`() {
        val names = AppEvents::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filterNot { it.name.startsWith("P_") }
            .map { it.get(null) as String }
        assertTrue(names.isNotEmpty())
        names.forEach { assertEquals(it, AnalyticsEvent.sanitizeName(it)) }
    }

    @Test
    fun `recording tracker logs screen_view for screens`() {
        val tracker = RecordingAnalyticsTracker()
        tracker.screen(AppEvents.Screen.DISCOVER)
        assertEquals("discover", tracker.currentScreen)
        assertEquals(listOf("screen_view"), tracker.names())
        assertEquals("discover", tracker.events.single().params["screen_name"])
    }
}
