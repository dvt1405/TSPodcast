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
