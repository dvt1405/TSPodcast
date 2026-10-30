package tss.t.shared.analytics

import tss.t.shared.playback.SleepTimerOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** M6-AC19: the nine M6 events (§3.8) fit Firebase's limits. */
class AnalyticsEventsTest {
    @Test
    fun nineNamesExactly() {
        assertEquals(
            listOf(
                "news_hub_open",
                "news_brief_play",
                "news_source_unresolved",
                "speed_change",
                "sleep_timer_set",
                "show_search",
                "show_hidden",
                "content_report",
                "shortcut_invoked",
            ),
            AnalyticsEvents.ALL,
        )
        assertEquals(9, AnalyticsEvents.ALL.toSet().size)
    }

    @Test
    fun namesAndParamsFitFirebase() {
        val params = listOf(
            AnalyticsEvents.PARAM_FROM, AnalyticsEvents.PARAM_COUNT, AnalyticsEvents.PARAM_SOURCE_KEY,
            AnalyticsEvents.PARAM_RATE, AnalyticsEvents.PARAM_SCOPE, AnalyticsEvents.PARAM_OPTION,
            AnalyticsEvents.PARAM_ORIGIN, AnalyticsEvents.PARAM_RESULT_COUNT, AnalyticsEvents.PARAM_TRUNCATED,
            AnalyticsEvents.PARAM_FEED_ID, AnalyticsEvents.PARAM_KIND, AnalyticsEvents.PARAM_INTENT,
        )
        for (name in AnalyticsEvents.ALL + params) {
            assertTrue(name.length <= AnalyticsDefaults.MAX_NAME_LENGTH, name)
            assertTrue(Regex("[a-z][a-z_]*").matches(name), name)
        }
    }

    @Test
    fun parameterValues() {
        assertEquals("1.5", AnalyticsEvents.rateValue(15))
        assertEquals("1.0", AnalyticsEvents.rateValue(10))
        assertEquals("0.8", AnalyticsEvents.rateValue(8))
        assertEquals("30", AnalyticsEvents.sleepTimerOption(SleepTimerOption.Minutes(30)))
        assertEquals("end_of_episode", AnalyticsEvents.sleepTimerOption(SleepTimerOption.EndOfEpisode))
        assertEquals("off", AnalyticsEvents.sleepTimerOption(SleepTimerOption.Off))
    }
}
