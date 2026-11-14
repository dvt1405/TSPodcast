package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterTrending
import tss.t.shared.blacklist.isBlockedSource
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.location.LanguageMatch
import tss.t.shared.location.PodcastLocation
import tss.t.shared.model.Episode
import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.newshub.NewsEpisode
import tss.t.shared.util.EpochClock

/**
 * Trending shows of one podcast location (its language only), for the "by location" lists of Home
 * and the topic page of Search (Android `GetPodcastsByLocation`): E1 `podcasts/trending` with
 * `lang=<contentCode>`, `max`, `since = now − sinceDays`, optional `cat`. Blacklist-filtered,
 * without shows titled in another script ([LanguageMatch.titleFits]), one entry per id.
 *
 * Cached in memory for 15 minutes per language, category, size and window, so switching location
 * chips does not refetch; [force] (pull-to-refresh) skips the cache. Only a `Success` is stored.
 */
class GetPodcastsByLocation internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
    private val cache: InMemoryTtlCache<String, List<Podcast>>,
    private val clock: EpochClock,
) {
    suspend operator fun invoke(
        location: PodcastLocation,
        cat: String? = null,
        max: Int = DEFAULT_MAX,
        sinceDays: Int = DEFAULT_SINCE_DAYS,
        force: Boolean = false,
    ): TSDataState<List<Podcast>> {
        val key = cacheKey(location, cat, max, sinceDays)
        if (!force) cache.get(key)?.let { return TSDataState.Success(it) }
        val nowSeconds = clock.nowMillis() / 1_000
        val result = api.getTrending(
            since = nowSeconds - sinceDays * DAY_SECONDS,
            max = max,
            lang = location.contentCode,
            cat = cat,
            notcat = null,
        )
        return when (result) {
            is TSDataState.Success -> {
                val items = result.data.filterTrending(blacklist).items
                    .filter { LanguageMatch.titleFits(it.title, location.contentCode) }
                    .distinctBy { it.id }
                cache.put(key, items)
                TSDataState.Success(items)
            }
            is TSDataState.Error -> TSDataState.Error(result.exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    companion object {
        /** Home: 40 shows of the last 7 days (Android defaults). */
        const val DEFAULT_MAX = 40
        const val DEFAULT_SINCE_DAYS = 7

        /** Search topic page (Android `SearchViewModel.selectCategory`): 60 shows, 30 days. */
        const val CATEGORY_MAX = 60
        const val CATEGORY_SINCE_DAYS = 30

        internal const val DAY_SECONDS = 24L * 60 * 60

        fun cacheKey(location: PodcastLocation, cat: String?, max: Int, sinceDays: Int): String =
            "${location.contentCode}|${cat?.trim().orEmpty()}|$max|$sinceDays"
    }
}

/**
 * "Thời sự" from the catalog (Android `GetLocationNews`): the newest episode (last [maxAgeHours])
 * of each trending News show in the location's language. Unlike the curated news hub
 * ([tss.t.shared.newshub.NewsHubRepository], which waits for publisher confirmation), this is
 * ordinary directory content like any list.
 *
 * 1. [GetPodcastsByLocation] with `cat=News`, `max=40`, 10 days; the first [maxShows] shows.
 * 2. One E10 `episodes/byfeedid` for all of them (`id=1,2,3`, `max=100`, `since = now − 72 h`).
 * 3. Per show its newest episode with audio, published in the window, not on a blocked host;
 *    newest first, at most [maxItems].
 *
 * Each item is a [NewsEpisode] with `sourceKey = "catalog:<feedId>"` and the show as its feed, so
 * the apps play and save it like a hub episode. Failures give an empty list (no section, as on
 * Android). Hidden shows and D6 are the caller's (see `HomeFeed.locationNews`).
 */
class GetLocationNews internal constructor(
    private val getPodcastsByLocation: GetPodcastsByLocation,
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
    private val clock: EpochClock,
) {
    suspend operator fun invoke(
        location: PodcastLocation,
        maxShows: Int = MAX_SHOWS,
        maxItems: Int = MAX_ITEMS,
        maxAgeHours: Long = MAX_AGE_HOURS,
        force: Boolean = false,
    ): List<NewsEpisode> {
        val shows = (
            getPodcastsByLocation(location, cat = NEWS_CATEGORY, max = 40, sinceDays = 10, force = force)
                as? TSDataState.Success
            )?.data.orEmpty().take(maxShows)
        if (shows.isEmpty()) return emptyList()
        val byId = shows.associateBy { it.id }
        val since = clock.nowMillis() / 1_000 - maxAgeHours * 3_600
        val result = api.getEpisodesByFeedId(id = shows.joinToString(",") { it.id.toString() }, max = 100, since = since)
        val episodes = (result as? TSDataState.Success)?.data?.items.orEmpty()
        return episodes
            .filter { it.datePublished >= since && it.enclosureUrl.isNotBlank() && !it.isBlockedSource(blacklist) }
            .groupBy { it.feedId }
            .mapNotNull { (feedId, list) ->
                val podcast = byId[feedId] ?: return@mapNotNull null
                NewsEpisode("$SOURCE_PREFIX$feedId", podcast.toFeed(), list.maxBy(Episode::datePublished))
            }
            .sortedByDescending { it.episode.datePublished }
            .take(maxItems)
    }

    companion object {
        /** PodcastIndex category name; the API accepts names or ids in `cat`. */
        const val NEWS_CATEGORY = "News"
        const val MAX_SHOWS = 15
        const val MAX_ITEMS = 12
        const val MAX_AGE_HOURS = 72L

        /** `sourceKey` prefix of catalog items (Android `NewsItem("catalog:$feedId", …)`). */
        const val SOURCE_PREFIX = "catalog:"
    }
}

/** A trending entry as the feed of a [NewsEpisode] (only what playing and saving it reads). */
internal fun Podcast.toFeed(): Feed = Feed(
    artwork = artwork?.takeIf { it.isNotBlank() } ?: image,
    author = author.orEmpty(),
    categories = categories,
    description = description.orEmpty(),
    explicit = explicit == 1,
    id = id,
    image = image.ifBlank { feedImage.orEmpty() },
    itunesId = itunesId,
    language = language.orEmpty(),
    link = link.orEmpty(),
    newestItemPubdate = newestItemPublishTime ?: datePublished,
    title = title.orEmpty(),
    url = url.orEmpty(),
)
