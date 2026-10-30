package tss.t.shared.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** M4-AC9: Android `TSAnalytics` defaults without the device id (D5), push event names, Firebase limits. */
class AnalyticsDefaultsTest {

    // 2023-11-14T22:13:20Z in seconds; in millis it would have 13 digits.
    private val nowSeconds = 1_700_000_000L

    @Test
    fun defaultsHaveExactlyTheAndroidKeysMinusDeviceId() {
        val defaults = AnalyticsDefaults.build(
            osVersion = "18.5",
            appVersion = "1.0 (7)",
            nowSeconds = nowSeconds,
            screen = "Home",
        )

        assertEquals(
            mapOf<String, Any>(
                "os" to "iOS",
                "os_version" to "18.5",
                "app_version" to "1.0 (7)",
                "event_time_stamp" to 1_700_000_000L,
                "screen" to "Home",
            ),
            defaults,
        )
        assertFalse(defaults.keys.any { it.startsWith("device") })
        assertEquals(10, (defaults.getValue("event_time_stamp") as Long).toString().length, "seconds, not millis")
    }

    @Test
    fun screenIsLeftOutWhenUnknown() {
        val defaults = AnalyticsDefaults.build("18.5", "1.0", nowSeconds, screen = null)

        assertEquals(setOf("os", "os_version", "app_version", "event_time_stamp"), defaults.keys)
    }

    @Test
    fun eventParamsFollowAndroidOrderAndOverrides() {
        val params = AnalyticsDefaults.eventParams(
            params = mapOf("CurrentPage" to "2", "app_version" to "custom", "screen" to "ignored", "event_time_stamp" to "0"),
            osVersion = "18.5",
            appVersion = "1.0",
            nowSeconds = nowSeconds,
            screen = "Onboarding",
        )

        assertEquals(
            listOf("os", "os_version", "app_version", "CurrentPage", "event_time_stamp", "screen"),
            params.keys.toList(),
        )
        assertEquals("custom", params["app_version"], "props override os/app defaults like bundle putAll")
        assertEquals(nowSeconds, params["event_time_stamp"], "the timestamp is always set last")
        assertEquals("Onboarding", params["screen"], "screen is always set last")
        assertEquals("2", params["CurrentPage"])
    }

    @Test
    fun eventParamsWithoutScreenKeepAParamNamedScreen() {
        val params = AnalyticsDefaults.eventParams(mapOf("screen" to "Player"), "18.5", "1.0", nowSeconds, screen = null)

        assertEquals("Player", params["screen"])
    }

    @Test
    fun screenEventNameIsLowercaseWithSuffix() {
        assertEquals("onboarding_screen", AnalyticsDefaults.screenEventName("Onboarding"))
    }

    @Test
    fun fcmReceiveEventNames() {
        assertEquals("fcm_new_podcasts_message_receive", AnalyticsDefaults.fcmReceiveEvent("new_podcasts"))
        assertEquals("fcm_null_message_receive", AnalyticsDefaults.fcmReceiveEvent(null))
        assertEquals("fcm_null_message_receive", AnalyticsDefaults.fcmReceiveEvent(""))
        assertEquals("fcm_null_message_receive", AnalyticsDefaults.fcmReceiveEvent("  "))
    }

    @Test
    fun longKeysAndValuesAreTrimmedToFirebaseLimits() {
        val longKey = "k".repeat(41)
        val longValue = "v".repeat(101)

        val params = AnalyticsDefaults.eventParams(mapOf(longKey to longValue), "1".repeat(120), "1.0", nowSeconds, "s".repeat(150))

        assertEquals(mapOf("k".repeat(40) to "v".repeat(100)), params.filterKeys { it.startsWith("k") })
        assertEquals(100, (params["os_version"] as String).length)
        assertEquals(100, (params["screen"] as String).length)
        assertEquals("k".repeat(40), AnalyticsDefaults.trimName(longKey))
        assertEquals("a".repeat(40), AnalyticsDefaults.trimName("a".repeat(40)), "40 is allowed")
        assertEquals("v".repeat(100), AnalyticsDefaults.trimValue("v".repeat(100)), "100 is allowed")
        val longType = "t".repeat(40)
        assertEquals(40, AnalyticsDefaults.fcmReceiveEvent(longType).length)
        assertEquals(40, AnalyticsDefaults.screenEventName("S".repeat(50)).length)
    }

    @Test
    fun trimmingNeverSplitsASurrogatePair() {
        // 99 ASCII characters, then an emoji (2 UTF-16 chars) across the 100 limit.
        val value = "a".repeat(99) + "🎧" + "tail"

        assertEquals("a".repeat(99), AnalyticsDefaults.trimValue(value))
    }
}
