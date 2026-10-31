package tss.t.shared.newshub

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.HiddenShowsRepository
import tss.t.shared.blacklist.isBlockedSource
import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import tss.t.shared.model.TSDataState
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.usecase.GetEpisodeByFeedId

/** One episode of the hub with the feed it belongs to (artwork, and the podcast saved with it). */
data class NewsEpisode(
    val sourceKey: String,
    val feed: Feed,
    val episode: Episode,
)

/** A configured source with its PodcastIndex feed and its newest episodes (newest first, after D6). */
data class ResolvedSource(
    val source: NewsSource,
    val feed: Feed,
    val episodes: List<Episode>,
) {
    val key: String get() = source.key
}

/**
 * Everything the hub screen and the Home section show (M6 §3.4).
 *
 * @property news resolved news sources in config order: the "Nguồn" grid.
 * @property night "Truyện đêm khuya" sources in config order.
 * @property nightSleepTimerMinutes the timer set when a night episode plays.
 * @property latest "Mới nhất": the [NewsHubRepository.LATEST_COUNT] newest episodes of [news],
 *   newest first.
 * @property brief "Điểm tin sáng", possibly empty (no bulletin in its time window).
 */
class NewsHub(
    val isEnabled: Boolean,
    val news: List<ResolvedSource>,
    val night: List<ResolvedSource>,
    val nightSleepTimerMinutes: Int,
    val latest: List<NewsEpisode>,
    val brief: MorningBrief,
) {
    /** Nothing to show: the Home section and the hub route are absent, never an empty screen. */
    val isEmpty: Boolean get() = !isEnabled || (news.isEmpty() && night.isEmpty())

    companion object {
        val Disabled = NewsHub(
            isEnabled = false,
            news = emptyList(),
            night = emptyList(),
            nightSleepTimerMinutes = NewsHubRepository.DEFAULT_NIGHT_SLEEP_TIMER_MINUTES,
            latest = emptyList(),
            brief = MorningBrief.Empty,
        )
    }
}

/**
 * Loads the news hub (M6 §3.4): every configured source in parallel through [NewsSourceResolver]
 * and `episodes/byfeedid?max=20` ([EPISODES_MAX], 15-minute cache key `"<id>?max=20"`).
 *
 * Per source, in order: unresolved (not found, dead, blocked) → left out; a show the user hid
 * (`HiddenShowsRepository`) → left out; D6 unless explicit content is allowed (explicit feed →
 * left out, explicit episodes dropped); episodes on a blocked host dropped; no episode left →
 * left out. No request ever goes to a publisher's RSS host.
 *
 * Result: `Success(NewsHub.Disabled)` without a request when the config is off;
 * `Error` only when no source loaded and at least one failed for a network or server reason
 * (so the Home section offers a retry); otherwise `Success` with whatever loaded.
 */
class NewsHubRepository internal constructor(
    private val config: NewsHubConfigRepository,
    private val resolver: NewsSourceResolver,
    private val getEpisodeByFeedId: GetEpisodeByFeedId,
    private val preferences: AppPreferences,
    private val hiddenShows: HiddenShowsRepository,
    private val blacklist: BlacklistRepository,
) {
    /** Whether the hub exists at all (config on and at least one news source). */
    val isEnabled: Boolean get() = config.isEnabled

    /** Receives each unresolved source key once per process, for `news_source_unresolved`. */
    var unresolvedListener: NewsSourceUnresolvedListener?
        get() = resolver.unresolvedListener
        set(value) {
            resolver.unresolvedListener = value
        }

    suspend fun load(nowMillis: Long): TSDataState<NewsHub> {
        val hubConfig = config.config
        if (!hubConfig.enabled) return TSDataState.Success(NewsHub.Disabled)
        val hidden = hiddenShows.hiddenIds()
        val allowExplicit = preferences.allowExplicitContent()
        val (news, night) = coroutineScope {
            val newsCalls = hubConfig.newsSources.map { async { loadSource(it, hidden, allowExplicit) } }
            val nightCalls = hubConfig.nightSources.map { async { loadSource(it, hidden, allowExplicit) } }
            newsCalls.awaitAll() to nightCalls.awaitAll()
        }
        val all = news + night
        if (all.none { it is SourceLoad.Loaded }) {
            all.firstNotNullOfOrNull { (it as? SourceLoad.Failed)?.exception }
                ?.let { return TSDataState.Error(it) }
        }
        val newsSources = news.mapNotNull { (it as? SourceLoad.Loaded)?.source }
        val nightSources = night.mapNotNull { (it as? SourceLoad.Loaded)?.source }
        return TSDataState.Success(
            NewsHub(
                isEnabled = true,
                news = newsSources,
                night = nightSources,
                nightSleepTimerMinutes = hubConfig.nightSleepTimerMinutes ?: DEFAULT_NIGHT_SLEEP_TIMER_MINUTES,
                latest = latest(newsSources),
                brief = MorningBrief.build(newsSources, nowMillis),
            ),
        )
    }

    private suspend fun loadSource(source: NewsSource, hidden: Set<Long>, allowExplicit: Boolean): SourceLoad {
        val feed = when (val resolution = resolver.resolve(source)) {
            is NewsSourceResolution.Resolved -> resolution.feed
            NewsSourceResolution.Unresolved -> return SourceLoad.Skipped
            is NewsSourceResolution.Failed -> return SourceLoad.Failed(resolution.exception)
        }
        if (feed.id in hidden) return SourceLoad.Skipped
        if (!allowExplicit && feed.explicit) return SourceLoad.Skipped
        val episodes = when (val result = getEpisodeByFeedId(feed.id.toString(), EPISODES_MAX)) {
            is TSDataState.Success -> result.data.items
            is TSDataState.Error -> return SourceLoad.Failed(result.exception)
            is TSDataState.Loading -> return SourceLoad.Skipped
        }
        val kept = episodes
            .filter { allowExplicit || it.explicit != EXPLICIT }
            .filterNot { it.feedId in hidden || it.isBlockedSource(blacklist) }
            .sortedByDescending { it.datePublished }
        if (kept.isEmpty()) return SourceLoad.Skipped
        return SourceLoad.Loaded(ResolvedSource(source, feed, kept))
    }

    private fun latest(sources: List<ResolvedSource>): List<NewsEpisode> =
        sources
            .flatMap { source -> source.episodes.map { NewsEpisode(source.key, source.feed, it) } }
            .distinctBy { it.episode.id }
            .sortedByDescending { it.episode.datePublished }
            .take(LATEST_COUNT)

    private sealed interface SourceLoad {
        class Loaded(val source: ResolvedSource) : SourceLoad
        data object Skipped : SourceLoad
        class Failed(val exception: Throwable) : SourceLoad
    }

    companion object {
        /** Episodes asked per source; also the `max` of their cache key. */
        const val EPISODES_MAX = 20

        /** Size of "Mới nhất". */
        const val LATEST_COUNT = 30

        /** When the night section sets no timer. */
        const val DEFAULT_NIGHT_SLEEP_TIMER_MINUTES = 30

        private const val EXPLICIT = 1
    }
}
