package tss.t.shared.analytics

/**
 * Event naming and default parameters of Android's `TSAnalytics` (features-spec §4), for the
 * Firebase tracker on iOS.
 *
 * Every event carries `os`, `os_version`, `app_version` and `event_time_stamp` (epoch
 * **seconds**); `trackEvent` adds `screen`. **[Sửa] D5:** Android also sends a device parameter
 * (the Android ID); iOS has no such parameter, the Install ID goes to Crashlytics only.
 *
 * Firebase drops events whose name or parameter key is longer than 40 characters and cuts
 * string values at 100, so [eventParams] trims both itself ([trimName], [trimValue]) and a long
 * push payload can never make an event disappear.
 */
object AnalyticsDefaults {
    const val KEY_OS = "os"
    const val KEY_OS_VERSION = "os_version"
    const val KEY_APP_VERSION = "app_version"
    const val KEY_EVENT_TIME_STAMP = "event_time_stamp"
    const val KEY_SCREEN = "screen"

    /** Android sends `Build.VERSION.RELEASE`; on iOS the OS name is constant and the version is separate. */
    const val OS_NAME = "iOS"

    /** Firebase limit for event names and parameter keys. */
    const val MAX_NAME_LENGTH = 40

    /** Firebase limit for string parameter values. */
    const val MAX_VALUE_LENGTH = 100

    /**
     * The default parameters of one event. [screen] is added only when not null (Android puts
     * it on `trackEvent` only, and a null value is dropped by Firebase anyway).
     */
    fun build(osVersion: String, appVersion: String, nowSeconds: Long, screen: String?): Map<String, Any> =
        buildMap {
            put(KEY_OS, OS_NAME)
            put(KEY_OS_VERSION, trimValue(osVersion))
            put(KEY_APP_VERSION, trimValue(appVersion))
            put(KEY_EVENT_TIME_STAMP, nowSeconds)
            if (screen != null) put(KEY_SCREEN, trimValue(screen))
        }

    /**
     * Everything one event sends, in Android's order: `os`, `os_version`, `app_version`, then
     * [params] (which may override those three, as `putAll` does on Android), then
     * `event_time_stamp` and `screen`, which always win. Keys and values are trimmed.
     */
    fun eventParams(
        params: Map<String, String>,
        osVersion: String,
        appVersion: String,
        nowSeconds: Long,
        screen: String?,
    ): Map<String, Any> {
        val defaults = build(osVersion, appVersion, nowSeconds, screen)
        val result = LinkedHashMap<String, Any>()
        defaults.filterKeys { it != KEY_EVENT_TIME_STAMP && it != KEY_SCREEN }.forEach { (k, v) -> result[k] = v }
        val screenValue = defaults[KEY_SCREEN]
        params.forEach { (key, value) ->
            val name = trimName(key)
            // Overwritten below anyway; skipping keeps the key order the same on every event.
            if (name == KEY_EVENT_TIME_STAMP || (name == KEY_SCREEN && screenValue != null)) return@forEach
            result[name] = trimValue(value)
        }
        result[KEY_EVENT_TIME_STAMP] = nowSeconds
        screenValue?.let { result[KEY_SCREEN] = it }
        return result
    }

    /** `trackScreen("Onboarding")` → `onboarding_screen` (Android `"${screenName.lowercase()}_screen"`). */
    fun screenEventName(screen: String): String = trimName("${screen.lowercase()}_screen")

    /**
     * Event logged for every push the app sees: `fcm_<type>_message_receive`
     * (Android `TSFcmServices`). A missing or blank type gives `fcm_null_message_receive`, the
     * string Kotlin builds on Android for a null `type`.
     */
    fun fcmReceiveEvent(type: String?): String {
        val name = type?.takeUnless { it.isBlank() } ?: "null"
        return trimName("fcm_${name}_message_receive")
    }

    /** Event name or parameter key cut to [MAX_NAME_LENGTH] characters. */
    fun trimName(name: String): String = cut(name, MAX_NAME_LENGTH)

    /** String value cut to [MAX_VALUE_LENGTH] characters (never in the middle of an emoji's surrogate pair). */
    fun trimValue(value: String): String = cut(value, MAX_VALUE_LENGTH)

    private fun cut(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
        return text.substring(0, end)
    }
}
