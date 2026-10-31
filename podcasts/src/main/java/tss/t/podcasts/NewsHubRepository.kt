package tss.t.podcasts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tss.t.core.storage.SharedPref
import tss.t.coreapi.API
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.sharedlibrary.utils.ConfigAPI
import javax.inject.Inject
import javax.inject.Singleton

/** One item of "Thời sự & Bản tin": the latest episode of a confirmed news source. */
data class NewsItem(
    val sourceKey: String,
    val podcast: Podcast,
    val episode: Episode,
)

/**
 * "Thời sự & Bản tin" (Android port of iOS M6 N2, compact): the configured news sources
 * (Remote Config `news_hub_v1`, else the bundled `news_hub_default.json`, same schema as iOS)
 * resolved on PodcastIndex, and the newest episode of each within its `maxAgeHours`.
 *
 * Release gate, as on iOS (cổng L4): only sources with a `confirmedAt` date count (publisher
 * confirmation the owner collects), and the hub shows only with at least [MIN_NEWS_SOURCES]
 * usable sources. Debug builds may include unconfirmed sources to test the UI.
 *
 * Resolution never fetches the publisher's RSS: `podcasts/byitunesid` (accepted only when the
 * returned feed is on the host of the configured URL, so a wrong id cannot bring an impostor
 * feed), else `podcasts/byfeedurl`. The feed id is cached for 7 days. Dead feeds and feeds the
 * [ContentFilter] rejects (block list, hidden by the user, explicit) are left out.
 */
@Singleton
class NewsHubRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: API,
    private val remoteConfig: ConfigAPI,
    private val contentFilter: ContentFilter,
    private val sharedPref: SharedPref,
) {
    private data class Source(
        val key: String,
        val feedUrl: String,
        val itunesId: Long?,
        val confirmed: Boolean,
        val maxAgeHours: Long,
    )

    suspend fun load(includeUnconfirmed: Boolean, nowMillis: Long = System.currentTimeMillis()): List<NewsItem> =
        withContext(Dispatchers.IO) {
            val sources = newsSources().filter { it.confirmed || includeUnconfirmed }
            if (sources.size < MIN_NEWS_SOURCES) return@withContext emptyList()
            val items = coroutineScope {
                sources.map { source -> async { runCatching { latest(source, nowMillis) }.getOrNull() } }.awaitAll()
            }.filterNotNull()
            if (items.size < MIN_NEWS_SOURCES) emptyList() else items.sortedByDescending { it.episode.datePublished }
        }

    private fun newsSources(): List<Source> {
        val config = runCatching { remoteConfig.getJSONObject(KEY_CONFIG) }.getOrNull()
            ?: runCatching {
                JSONObject(context.assets.open("news_hub_default.json").bufferedReader().use { it.readText() })
            }.getOrNull()
            ?: return emptyList()
        if (!config.optBoolean("enabled", false)) return emptyList()
        val sections = config.optJSONArray("sections") ?: return emptyList()
        val news = (0 until sections.length()).map { sections.getJSONObject(it) }
            .firstOrNull { it.optString("id") == "news" } ?: return emptyList()
        val list = news.optJSONArray("sources") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = o.optString("feedUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Source(
                key = key,
                feedUrl = url,
                itunesId = o.optLong("itunesId", 0L).takeIf { it > 0 },
                confirmed = !o.isNull("confirmedAt") && o.optString("confirmedAt").isNotBlank(),
                maxAgeHours = o.optJSONObject("brief")?.optLong("maxAgeHours", 24L) ?: 24L,
            )
        }
    }

    private suspend fun latest(source: Source, nowMillis: Long): NewsItem? {
        val feed = resolve(source) ?: return null
        if (feed.dead != 0 || !contentFilter.allows(feed)) return null
        val episodes = (api.getEpisodeByFeedId(id = feed.id.toString(), max = 5, since = null, enclosure = null, fulltext = null, pretty = null)
                as? TSDataState.Success)?.data?.items.orEmpty()
        val minPublished = nowMillis / 1000 - source.maxAgeHours * 3600
        val episode = episodes
            .filter { it.datePublished >= minPublished && contentFilter.allows(it) }
            .maxByOrNull { it.datePublished } ?: return null
        return NewsItem(source.key, Podcast.fromFeed(feed), episode)
    }

    private fun resolve(source: Source): Feed? {
        val cacheKey = "newsHub.feedId.${source.key}"
        val cachedId = sharedPref.get<String>(cacheKey)
            ?.split('|')
            ?.takeIf { it.size == 3 && it[2] == source.feedUrl && System.currentTimeMillis() - (it[1].toLongOrNull() ?: 0L) < CACHE_TTL_MS }
            ?.first()
        if (cachedId != null) {
            (api.getPodcastByFeedId(cachedId, null) as? TSDataState.Success)?.data?.feed?.let { return it }
        }
        val expectedHost = BlacklistRepositoryImpl.hostOf(source.feedUrl)
        val byItunes = source.itunesId?.let { (api.getPodcastByItunesId(it) as? TSDataState.Success)?.data?.feed }
            ?.takeIf { feed ->
                feed.id > 0 && expectedHost != null &&
                    (BlacklistRepositoryImpl.hostOf(feed.url) == expectedHost ||
                        BlacklistRepositoryImpl.hostOf(feed.originalUrl) == expectedHost)
            }
        val feed = byItunes
            ?: (api.getPodcastByFeedUrl(source.feedUrl.trim()) as? TSDataState.Success)?.data?.feed?.takeIf { it.id > 0 }
            ?: return null
        sharedPref.save(cacheKey, "${feed.id}|${System.currentTimeMillis()}|${source.feedUrl}")
        return feed
    }

    companion object {
        const val KEY_CONFIG = "news_hub_v1"
        const val MIN_NEWS_SOURCES = 3
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
