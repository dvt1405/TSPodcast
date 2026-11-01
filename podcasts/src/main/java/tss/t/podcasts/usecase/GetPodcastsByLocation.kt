package tss.t.podcasts.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tss.t.core.repository.IPodcastRepository
import tss.t.core.storage.PodcastLocation
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.LanguageMatch
import tss.t.podcasts.NewsItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trending shows of one podcast location (its language only), for the "by location" lists on
 * Home. Cached in memory for 15 minutes per location and category, so switching location chips
 * does not refetch.
 */
@Singleton
class GetPodcastsByLocation @Inject constructor(
    private val repository: IPodcastRepository,
) {
    private data class Entry(val at: Long, val items: List<Podcast>)

    private val cache = mutableMapOf<String, Entry>()

    suspend operator fun invoke(
        location: PodcastLocation,
        cat: String? = null,
        max: Int = 40,
        sinceDays: Int = 7,
        force: Boolean = false,
    ): TSDataState<List<Podcast>> = withContext(Dispatchers.IO) {
        val key = "${location.contentCode}|${cat.orEmpty()}|$max"
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[key] }
            ?.takeIf { !force && now - it.at < TTL_MS }
            ?.let { return@withContext TSDataState.Success(it.items) }
        val result = runCatching {
            repository.getTrending(
                max = max,
                since = ((now - sinceDays * DAY_MS) / 1000).toInt(),
                lang = location.contentCode,
                cat = cat,
                notcat = null,
                pretty = null,
            )
        }.getOrElse { TSDataState.Error(it) }
        when (result) {
            is TSDataState.Success -> {
                val items = result.data.items
                    .filter { LanguageMatch.titleFits(it.title, location.contentCode) }
                    .distinctBy { it.id }
                synchronized(cache) { cache[key] = Entry(now, items) }
                TSDataState.Success(items)
            }
            is TSDataState.Error -> TSDataState.Error(result.exception)
            else -> TSDataState.Error(IllegalStateException("trending failed"))
        }
    }

    companion object {
        private const val TTL_MS = 15 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * "Thời sự" from the catalog: the newest episode (last [maxAgeHours]) of each trending News show
 * in the location's language. Unlike the curated news hub ([tss.t.podcasts.NewsHubRepository],
 * which waits for publisher confirmation), this is ordinary directory content like any list.
 */
class GetLocationNews @Inject constructor(
    private val getPodcastsByLocation: GetPodcastsByLocation,
    private val repository: IPodcastRepository,
) {
    suspend operator fun invoke(
        location: PodcastLocation,
        maxShows: Int = 15,
        maxItems: Int = 12,
        maxAgeHours: Long = 72,
    ): List<NewsItem> = withContext(Dispatchers.IO) {
        val shows = (getPodcastsByLocation(location, cat = NEWS_CATEGORY, max = 40, sinceDays = 10)
                as? TSDataState.Success)?.data.orEmpty().take(maxShows)
        if (shows.isEmpty()) return@withContext emptyList()
        val byId = shows.associateBy { it.id }
        val since = System.currentTimeMillis() / 1000 - maxAgeHours * 3600
        val episodes = runCatching {
            repository.getEpisodeByFeedId(
                id = shows.joinToString(",") { it.id.toString() },
                max = 100,
                since = since,
                enclosure = null,
                fulltext = null,
                pretty = null,
            ).first()
        }.getOrNull()
        val items = (episodes as? TSDataState.Success)?.data?.items.orEmpty()
        items.filter { it.datePublished >= since && !(it.enclosureUrl as String?).isNullOrBlank() }
            .groupBy { it.feedId }
            .mapNotNull { (feedId, list) ->
                val podcast = byId[feedId] ?: return@mapNotNull null
                NewsItem("catalog:$feedId", podcast, list.maxBy { it.datePublished })
            }
            .sortedByDescending { it.episode.datePublished }
            .take(maxItems)
    }

    companion object {
        /** PodcastIndex category name; the API accepts names or ids in `cat`. */
        const val NEWS_CATEGORY = "News"
    }
}
