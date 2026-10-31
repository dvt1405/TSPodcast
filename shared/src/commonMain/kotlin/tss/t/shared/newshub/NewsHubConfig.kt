package tss.t.shared.newshub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The editorial configuration of "Thời sự & Bản tin" (M6 §3.4, N2), read from Remote Config
 * `news_hub_v1` or the bundled `shared/assets/news_hub_default.json`.
 *
 * Sources are named by their public RSS URL and iTunes id only. The PodcastIndex feed id is
 * resolved on the device ([NewsSourceResolver]) and never written here, so the file reads like a
 * list of publishers, not of database rows.
 */
@Serializable
data class NewsHubConfig(
    val version: Int = 1,
    val enabled: Boolean = true,
    val sections: List<NewsSection> = emptyList(),
) {
    /** The section with [id], or null. */
    fun section(id: String): NewsSection? = sections.firstOrNull { it.id == id }

    /** Sources of [SECTION_NEWS], in config order. */
    val newsSources: List<NewsSource> get() = section(SECTION_NEWS)?.sources.orEmpty()

    /** Sources of [SECTION_NIGHT], in config order. */
    val nightSources: List<NewsSource> get() = section(SECTION_NIGHT)?.sources.orEmpty()

    /** The night shelf's sleep timer, null when the config sets none. */
    val nightSleepTimerMinutes: Int? get() = section(SECTION_NIGHT)?.sleepTimerMinutes

    companion object {
        /** News and bulletins: the morning brief, "Mới nhất" and the source grid. */
        const val SECTION_NEWS = "news"

        /** "Truyện đêm khuya": played with a sleep timer. */
        const val SECTION_NIGHT = "night"

        /** The configuration that hides the hub. */
        val Disabled = NewsHubConfig(enabled = false)

        /** Config JSON, lenient on unknown keys so a newer Remote Config value still reads. */
        internal val ConfigJson: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** Decodes [text], or null when it is blank or not a config object. */
        fun parse(text: String?, json: Json = ConfigJson): NewsHubConfig? {
            if (text.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        }
    }
}

/** A group of sources: [NewsHubConfig.SECTION_NEWS] or [NewsHubConfig.SECTION_NIGHT]. */
@Serializable
data class NewsSection(
    val id: String,
    val sleepTimerMinutes: Int? = null,
    val sources: List<NewsSource> = emptyList(),
)

/**
 * One publisher's podcast.
 *
 * @property key stable name used for the device cache (`newsHub.feed.<key>`) and analytics.
 * @property feedUrl the publisher's RSS URL. Only its host is compared and it is sent to
 *   PodcastIndex `podcasts/byfeedurl`; the app never downloads it.
 * @property itunesId tried first with `podcasts/byitunesid`, accepted only when the feed it
 *   returns lives on the host of [feedUrl].
 * @property confirmedAt `YYYY-MM-DD` on which the publisher agreed; null hides the source.
 * @property brief how the source feeds the morning brief; null keeps it out of the brief.
 */
@Serializable
data class NewsSource(
    val key: String,
    val feedUrl: String,
    val itunesId: Long? = null,
    val confirmedAt: String? = null,
    val brief: NewsBriefRule? = null,
) {
    /** [confirmedAt] is an ISO date (`2026-11-02`). Any other value counts as not confirmed. */
    val isConfirmed: Boolean get() = confirmedAt?.trim()?.let(ISO_DATE::matches) == true

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

/**
 * How many episodes of a source go into the morning brief ([MorningBrief]).
 *
 * @property take newest episodes kept, at least 1.
 * @property maxAgeHours only episodes published at most this many hours ago.
 * @property preferTitle before [preferTitleBeforeHour] (UTC+7), the newest episode whose title
 *   contains this text (accents and case ignored) goes first, e.g. VOV's "Thời sự 6h".
 * @property preferTitleBeforeHour hour of the day in UTC+7; null means at any hour.
 */
@Serializable
data class NewsBriefRule(
    val take: Int = 1,
    val maxAgeHours: Int = DEFAULT_MAX_AGE_HOURS,
    val preferTitle: String? = null,
    val preferTitleBeforeHour: Int? = null,
) {
    companion object {
        const val DEFAULT_MAX_AGE_HOURS = 24
    }
}
