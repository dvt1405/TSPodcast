package tss.t.sharedlibrary.analytics

/**
 * Catalogue of every app event (ads are in `tss.t.ads.AdsAnalytics`). The Vietnamese
 * reference with where each one fires is docs/done/analytics/analytics-events.md; keep both
 * in step. Names already used on iOS (`speed_change`, `sleep_timer_set`, `show_hidden`,
 * `content_report`, `shortcut_invoked`, shared `AnalyticsEvents`) keep the same name and
 * parameters here.
 *
 * Privacy (privacy policy §3.1, iOS M6 §3.8): no search text, no email, no device id. Ids of
 * public catalogue items (feed id, episode id, radio id, category id) are fine.
 */
object AppEvents {

    // ----- Names -----------------------------------------------------------------------------

    const val SCREEN_VIEW = "screen_view"
    const val SELECT_CONTENT = "select_content"
    const val SEARCH = "search"
    const val SEARCH_NO_RESULTS = "search_no_results"
    const val SHARE = "share"
    const val TUTORIAL_COMPLETE = "tutorial_complete"

    const val PLAY_START = "play_start"
    const val PLAYBACK_PAUSE = "playback_pause"
    const val PLAYBACK_RESUME = "playback_resume"
    const val PLAYBACK_SEEK = "playback_seek"
    const val PLAYBACK_SKIP = "playback_skip"
    const val PLAYBACK_PROGRESS = "playback_progress"
    const val PLAYBACK_COMPLETE = "playback_complete"
    const val PLAYBACK_ERROR = "playback_error"
    const val SPEED_CHANGE = "speed_change"
    const val SLEEP_TIMER_SET = "sleep_timer_set"

    const val FAVOURITE_ADD = "favourite_add"
    const val FAVOURITE_REMOVE = "favourite_remove"
    const val SHOW_HIDDEN = "show_hidden"
    const val SHOW_UNHIDDEN = "show_unhidden"
    const val CONTENT_REPORT = "content_report"
    const val SHORTCUT_INVOKED = "shortcut_invoked"

    const val CATEGORY_SELECT = "category_select"
    const val ONBOARDING_CATEGORIES = "onboarding_categories_saved"
    const val LOCATION_CHIP_SELECT = "location_chip_select"
    const val PODCAST_LOCATIONS_CHANGE = "podcast_locations_change"
    const val APP_LANGUAGE_CHANGE = "app_language_change"
    const val LIQUID_GLASS_TOGGLE = "liquid_glass_toggle"
    const val EXPLICIT_TOGGLE = "explicit_toggle"
    const val CRASH_REPORTS_TOGGLE = "crash_reports_toggle"

    const val API_ERROR = "api_error"

    // ----- Shared parameter keys -------------------------------------------------------------

    const val P_CONTENT_TYPE = "content_type"
    const val P_ITEM_ID = "item_id"
    const val P_FEED_ID = "feed_id"
    const val P_SECTION = "section"
    const val P_REGION = "region"
    const val P_POSITION = "position"
    const val P_SCREEN = "screen"

    /** `content_type` values. */
    object ContentType {
        const val PODCAST = "podcast"
        const val EPISODE = "episode"
        const val LIVE = "live"
        const val RADIO = "radio"
        const val NEWS = "news"
        const val CATEGORY = "category"
    }

    /** `section` values of [selectContent]: where on which screen the user tapped. */
    object Section {
        const val TRENDING = "trending"
        const val NEWS = "news"
        const val LOCATION = "location"
        const val MUSIC = "music"
        const val LIVE = "live"
        const val RECENT = "recent"
        const val FAVOURITE_CATEGORIES = "favourite_categories"
        const val SEARCH_RESULTS = "search_results"
        const val FAVOURITES = "favourites"
        const val RADIO = "radio"
        const val PODCAST_DETAIL = "podcast_detail"
    }

    /** `screen_name` values of [screenView]. */
    object Screen {
        const val DISCOVER = "discover"
        const val FAVOURITES = "favourites"
        const val SEARCH = "search"
        const val RADIO = "radio"
        const val PLAYER = "player"
        const val PODCAST_DETAIL = "podcast_detail"
        const val SETTINGS = "settings"
        const val ONBOARDING_CATEGORIES = "onboarding_categories"
        const val LOCATION_SHEET = "podcast_location_sheet"
        const val PRIVACY_SHEET = "privacy_consent_sheet"

        /** `onboarding_1` … `onboarding_n` (1-based page). */
        fun onboardingStep(page: Int) = "onboarding_$page"
    }

    /** GA4 user properties (≤ 24 characters each). */
    object UserProperty {
        const val PODCAST_LOCATIONS = "podcast_locations"
        const val PRIMARY_LOCATION = "primary_location"
        const val APP_LANGUAGE = "app_language"
        const val LIQUID_GLASS = "liquid_glass"
        const val EXPLICIT_ALLOWED = "explicit_allowed"

        /** GA4 cuts user property values at 36 characters. */
        const val MAX_VALUE = 36
    }

    // ----- Screens & navigation --------------------------------------------------------------

    fun screenView(screenName: String, screenClass: String = screenName) = AnalyticsEvent.of(
        SCREEN_VIEW, "screen_name" to screenName, "screen_class" to screenClass,
    )

    /**
     * GA4 `select_content`: a tap on a podcast, episode, live item, news bulletin, radio
     * station or category. [section] says where (see [Section]); [region] for the location
     * chart and news by location.
     */
    fun selectContent(
        contentType: String,
        itemId: Any?,
        section: String,
        region: String? = null,
        position: Int? = null,
    ) = AnalyticsEvent.of(
        SELECT_CONTENT,
        P_CONTENT_TYPE to contentType,
        P_ITEM_ID to itemId?.toString(),
        P_SECTION to section,
        P_REGION to region,
        P_POSITION to position,
    )

    fun share(contentType: String, itemId: Any?, method: String = "system_sheet") = AnalyticsEvent.of(
        SHARE, P_CONTENT_TYPE to contentType, P_ITEM_ID to itemId?.toString(), "method" to method,
    )

    fun tutorialComplete(analyticsAllowed: Boolean, crashReportsAllowed: Boolean) = AnalyticsEvent.of(
        TUTORIAL_COMPLETE,
        "analytics_allowed" to analyticsAllowed,
        "crash_reports_allowed" to crashReportsAllowed,
    )

    fun shortcutInvoked(intent: String) = AnalyticsEvent.of(SHORTCUT_INVOKED, "intent" to intent)

    // ----- Discovery -------------------------------------------------------------------------

    /**
     * GA4 `search`, once per settled query (not per keystroke). The text itself is never sent,
     * only its length and word count.
     */
    fun search(term: String, mode: String, resultCount: Int) = AnalyticsEvent.of(
        SEARCH,
        "term_length" to term.trim().length,
        "word_count" to term.trim().split(Regex("\\s+")).count { it.isNotEmpty() },
        "search_mode" to mode,
        "result_count" to resultCount,
    )

    fun searchNoResults(term: String, mode: String) = AnalyticsEvent.of(
        SEARCH_NO_RESULTS,
        "term_length" to term.trim().length,
        "search_mode" to mode,
    )

    /** A topic chip: `origin` = `search` | `onboarding`. */
    fun categorySelect(categoryId: Any?, categoryName: String?, origin: String) = AnalyticsEvent.of(
        CATEGORY_SELECT,
        "category_id" to categoryId?.toString(),
        "category_name" to categoryName,
        "origin" to origin,
    )

    fun onboardingCategoriesSaved(count: Int) = AnalyticsEvent.of(ONBOARDING_CATEGORIES, "count" to count)

    fun locationChipSelect(region: String, section: String) = AnalyticsEvent.of(
        LOCATION_CHIP_SELECT, P_REGION to region, P_SECTION to section,
    )

    fun podcastLocationsChange(regions: List<String>, previous: List<String>) = AnalyticsEvent.of(
        PODCAST_LOCATIONS_CHANGE,
        "count" to regions.size,
        "primary_location" to regions.firstOrNull(),
        "locations" to regions.joinToString(","),
        "primary_changed" to (regions.firstOrNull() != previous.firstOrNull()),
        "change" to when {
            regions.size > previous.size -> "add"
            regions.size < previous.size -> "remove"
            else -> "reorder"
        },
    )

    fun appLanguageChange(language: String, previous: String?) = AnalyticsEvent.of(
        APP_LANGUAGE_CHANGE, "language" to language, "previous_language" to previous,
    )

    fun liquidGlassToggle(enabled: Boolean) = AnalyticsEvent.of(LIQUID_GLASS_TOGGLE, "enabled" to enabled)

    fun explicitToggle(allowed: Boolean) = AnalyticsEvent.of(EXPLICIT_TOGGLE, "allowed" to allowed)

    fun crashReportsToggle(enabled: Boolean) = AnalyticsEvent.of(CRASH_REPORTS_TOGGLE, "enabled" to enabled)

    // ----- Content actions -------------------------------------------------------------------

    fun favourite(added: Boolean, contentType: String, itemId: Any?) = AnalyticsEvent.of(
        if (added) FAVOURITE_ADD else FAVOURITE_REMOVE,
        P_CONTENT_TYPE to contentType,
        P_ITEM_ID to itemId?.toString(),
    )

    fun showHidden(feedId: Long) = AnalyticsEvent.of(SHOW_HIDDEN, P_FEED_ID to feedId)

    fun showUnhidden(feedId: Long) = AnalyticsEvent.of(SHOW_UNHIDDEN, P_FEED_ID to feedId)

    /** `kind` = `feed` | `episode` | `hub` (iOS parity). */
    fun contentReport(kind: String, feedId: Long?) = AnalyticsEvent.of(
        CONTENT_REPORT, "kind" to kind, P_FEED_ID to feedId,
    )

    // ----- Playback --------------------------------------------------------------------------

    /** Common identity of the item being played. */
    data class PlaybackItem(
        val contentType: String,
        val itemId: String?,
        val feedId: Long? = null,
    ) {
        internal fun params(): List<Pair<String, Any?>> = listOf(
            P_CONTENT_TYPE to contentType,
            P_ITEM_ID to itemId,
            P_FEED_ID to feedId,
        )
    }

    /** First time an item actually starts playing (after buffering). */
    fun playStart(item: PlaybackItem, startPositionSec: Long, screen: String?) = AnalyticsEvent.of(
        PLAY_START,
        item.params() + listOf(
            "start_position_sec" to startPositionSec,
            "resumed" to (startPositionSec > 0),
            P_SCREEN to screen,
        ),
    )

    fun playbackPause(item: PlaybackItem, positionSec: Long, percent: Int?) = AnalyticsEvent.of(
        PLAYBACK_PAUSE,
        item.params() + listOf("position_sec" to positionSec, "percent" to percent),
    )

    fun playbackResume(item: PlaybackItem, positionSec: Long) = AnalyticsEvent.of(
        PLAYBACK_RESUME, item.params() + listOf("position_sec" to positionSec),
    )

    /** A scrub on the progress bar (not the ± buttons, see [playbackSkip]). */
    fun playbackSeek(item: PlaybackItem, fromSec: Long, toSec: Long) = AnalyticsEvent.of(
        PLAYBACK_SEEK,
        item.params() + listOf("from_sec" to fromSec, "to_sec" to toSec, "delta_sec" to (toSec - fromSec)),
    )

    /** `direction` = `back` (−15 s) | `forward` (+30 s) | `next` | `previous` (queue). */
    fun playbackSkip(item: PlaybackItem, direction: String) = AnalyticsEvent.of(
        PLAYBACK_SKIP, item.params() + listOf("direction" to direction),
    )

    /** 25 / 50 / 75 % of an on-demand item reached, once per item and play. */
    fun playbackProgress(item: PlaybackItem, percent: Int, durationSec: Long) = AnalyticsEvent.of(
        PLAYBACK_PROGRESS,
        item.params() + listOf("percent" to percent, "duration_sec" to durationSec),
    )

    fun playbackComplete(item: PlaybackItem, durationSec: Long) = AnalyticsEvent.of(
        PLAYBACK_COMPLETE, item.params() + listOf("duration_sec" to durationSec),
    )

    /** Media3 `PlaybackException`: [codeName] e.g. `ERROR_CODE_IO_BAD_HTTP_STATUS`. */
    fun playbackError(item: PlaybackItem?, codeName: String, code: Int) = AnalyticsEvent.of(
        PLAYBACK_ERROR,
        (item?.params() ?: emptyList()) + listOf(
            "error_code" to codeName.removePrefix("ERROR_CODE_").lowercase(),
            "error_code_num" to code,
        ),
    )

    /** iOS parity: `rate` = `"1.5"`, `scope` = `show` | `all`. */
    fun speedChange(tenths: Int, applyToAll: Boolean) = AnalyticsEvent.of(
        SPEED_CHANGE,
        "rate" to "${tenths / 10}.${tenths % 10}",
        "scope" to if (applyToAll) "all" else "show",
    )

    /** iOS parity: `option` = minutes | `end_of_episode` | `off`; `origin` = `player` | `shortcut`. */
    fun sleepTimerSet(option: String, origin: String) = AnalyticsEvent.of(
        SLEEP_TIMER_SET, "option" to option, "origin" to origin,
    )

    // ----- Errors ----------------------------------------------------------------------------

    /**
     * A failed API call. [endpoint] is the URL path with ids replaced (`/api/1.0/podcasts/byfeedid`),
     * [httpCode] 0 when no response came back, [errorType] `http` | `timeout` | `network` | `other`.
     */
    fun apiError(endpoint: String, httpCode: Int, errorType: String, screen: String?) = AnalyticsEvent.of(
        API_ERROR,
        "endpoint" to endpoint,
        "http_code" to httpCode,
        "error_type" to errorType,
        P_SCREEN to screen,
    )
}
