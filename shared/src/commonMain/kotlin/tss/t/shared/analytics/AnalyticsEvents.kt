package tss.t.shared.analytics

import tss.t.shared.playback.SleepTimerOption

/**
 * Event names and parameters added in M6 (§3.8), for both apps. Every name fits Firebase's
 * [AnalyticsDefaults.MAX_NAME_LENGTH]. No event carries a search query, a title or an email.
 */
object AnalyticsEvents {
    /** The news hub screen opened; [PARAM_FROM] = `home_seeAll` | `home_brief`. */
    const val NEWS_HUB_OPEN = "news_hub_open"

    /** "Play all" of the morning brief; [PARAM_COUNT]. */
    const val NEWS_BRIEF_PLAY = "news_brief_play"

    /** A news source did not resolve (once per process and key); [PARAM_SOURCE_KEY]. */
    const val NEWS_SOURCE_UNRESOLVED = "news_source_unresolved"

    /** Playback speed changed (sheet or Lock Screen); [PARAM_RATE] (`"1.5"`), [PARAM_SCOPE]. */
    const val SPEED_CHANGE = "speed_change"

    /** Sleep timer set; [PARAM_OPTION] ([sleepTimerOption]), [PARAM_ORIGIN]. */
    const val SLEEP_TIMER_SET = "sleep_timer_set"

    /** First search inside a show with results; [PARAM_RESULT_COUNT], [PARAM_TRUNCATED]. */
    const val SHOW_SEARCH = "show_search"

    /** A show was hidden; [PARAM_FEED_ID]. */
    const val SHOW_HIDDEN = "show_hidden"

    /** The report mail opened; [PARAM_KIND] = `feed` | `episode` | `hub`. */
    const val CONTENT_REPORT = "content_report"

    /** An App Shortcut ran; [PARAM_INTENT]. */
    const val SHORTCUT_INVOKED = "shortcut_invoked"

    /** The nine names above, in the order of M6 §3.8. */
    val ALL: List<String> = listOf(
        NEWS_HUB_OPEN,
        NEWS_BRIEF_PLAY,
        NEWS_SOURCE_UNRESOLVED,
        SPEED_CHANGE,
        SLEEP_TIMER_SET,
        SHOW_SEARCH,
        SHOW_HIDDEN,
        CONTENT_REPORT,
        SHORTCUT_INVOKED,
    )

    /**
     * The podcast locations changed (Settings or Home → "Vị trí podcast"), Android
     * `AppEvents.podcastLocationsChange`; params from [podcastLocationsChangeParams].
     */
    const val PODCAST_LOCATIONS_CHANGE = "podcast_locations_change"

    /** A location chip of Home; [PARAM_REGION], [PARAM_SECTION] = [SECTION_LOCATION] | [SECTION_NEWS]. */
    const val LOCATION_CHIP_SELECT = "location_chip_select"

    /** The podcast location events, same names and parameters as Android. */
    val LOCATION_EVENTS: List<String> = listOf(PODCAST_LOCATIONS_CHANGE, LOCATION_CHIP_SELECT)

    const val PARAM_REGION = "region"
    const val PARAM_SECTION = "section"
    const val PARAM_PRIMARY_LOCATION = "primary_location"
    const val PARAM_LOCATIONS = "locations"
    const val PARAM_PRIMARY_CHANGED = "primary_changed"
    const val PARAM_CHANGE = "change"

    /** [PARAM_SECTION] of [LOCATION_CHIP_SELECT]: "Podcast theo vị trí" or "Thời sự". */
    const val SECTION_LOCATION = "location"
    const val SECTION_NEWS = "news"

    /**
     * Parameters of [PODCAST_LOCATIONS_CHANGE] for [regions] (new, priority order) after
     * [previous]: `count`, `primary_location`, `locations` (`VN,US`), `primary_changed`
     * (`true`/`false`) and `change` = `add` | `remove` | `reorder`.
     */
    fun podcastLocationsChangeParams(regions: List<String>, previous: List<String>): Map<String, String> = buildMap {
        put(PARAM_COUNT, regions.size.toString())
        regions.firstOrNull()?.let { put(PARAM_PRIMARY_LOCATION, it) }
        put(PARAM_LOCATIONS, regions.joinToString(","))
        put(PARAM_PRIMARY_CHANGED, (regions.firstOrNull() != previous.firstOrNull()).toString())
        put(
            PARAM_CHANGE,
            when {
                regions.size > previous.size -> "add"
                regions.size < previous.size -> "remove"
                else -> "reorder"
            },
        )
    }

    const val PARAM_FROM = "from"
    const val PARAM_COUNT = "count"
    const val PARAM_SOURCE_KEY = "source_key"
    const val PARAM_RATE = "rate"
    const val PARAM_SCOPE = "scope"
    const val PARAM_OPTION = "option"
    const val PARAM_ORIGIN = "origin"
    const val PARAM_RESULT_COUNT = "result_count"
    const val PARAM_TRUNCATED = "truncated"
    const val PARAM_FEED_ID = "feed_id"
    const val PARAM_KIND = "kind"
    const val PARAM_INTENT = "intent"

    /** [PARAM_SCOPE]: the speed was stored for one show, or for every show. */
    const val SCOPE_SHOW = "show"
    const val SCOPE_ALL = "all"

    /** [PARAM_ORIGIN] of [SLEEP_TIMER_SET]. */
    const val ORIGIN_PLAYER = "player"
    const val ORIGIN_NIGHT_SHELF = "night_shelf"
    const val ORIGIN_SHORTCUT = "shortcut"

    /** [PARAM_RATE] of [SPEED_CHANGE]: `15` → `"1.5"`. */
    fun rateValue(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

    /** [PARAM_OPTION] of [SLEEP_TIMER_SET]: the minutes, `end_of_episode` or `off`. */
    fun sleepTimerOption(option: SleepTimerOption): String = when (option) {
        is SleepTimerOption.Minutes -> option.minutes.toString()
        SleepTimerOption.EndOfEpisode -> "end_of_episode"
        SleepTimerOption.Off -> "off"
    }
}
