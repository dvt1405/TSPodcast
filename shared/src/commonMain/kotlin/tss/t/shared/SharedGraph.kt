package tss.t.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.config.ConfigAPI
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.EpisodeResponse
import tss.t.shared.model.PodcastByFeedIdRes
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.CredentialsProvider
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.network.PodcastIndexAuth
import tss.t.shared.network.PodcastIndexJson
import tss.t.shared.network.createHttpClient
import tss.t.shared.network.defaultHttpClientEngine
import tss.t.shared.usecase.GetCategories
import tss.t.shared.usecase.GetEpisodeByFeedId
import tss.t.shared.usecase.GetLiveEpisodes
import tss.t.shared.usecase.GetMusicFeeds
import tss.t.shared.usecase.GetPodcastByFeedID
import tss.t.shared.usecase.GetRecentFeeds
import tss.t.shared.usecase.GetTrendingPodcasts
import tss.t.shared.usecase.SearchMusicFeeds
import tss.t.shared.usecase.SearchPodcasts
import tss.t.shared.util.EpochClock
import kotlin.time.Duration

/**
 * Hand-written composition root of the shared module (M0 §3.6; no Koin yet). Create **one**
 * per process and keep it: it owns the `HttpClient`, the blacklist and the in-memory caches,
 * so a second instance would start with empty caches.
 *
 * The blacklist is loaded synchronously while the graph is built, so the first request
 * made through it is already filtered.
 *
 * From Swift: `SharedGraph(credentials: BundleCredentialsProvider(), config: NoOpConfig())`.
 */
class SharedGraph(
    credentials: CredentialsProvider,
    config: ConfigAPI,
    engine: HttpClientEngine,
    clock: EpochClock,
) {
    /** Platform engine (OkHttp / Darwin) and the system clock. */
    constructor(credentials: CredentialsProvider, config: ConfigAPI) :
        this(credentials, config, defaultHttpClientEngine(), EpochClock.System)

    private val json: Json = PodcastIndexJson

    private val httpClient: HttpClient =
        createHttpClient(engine, PodcastIndexAuth(credentials, clock), json)

    private val api = PodcastIndexApi(httpClient, credentials, json)

    val blacklistRepository: BlacklistRepository = BlacklistRepository(config, json)

    private val trendingCache =
        InMemoryTtlCache<String, TrendingPodcastRes>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val episodesByFeedCache =
        InMemoryTtlCache<String, EpisodeResponse>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val podcastByFeedCache =
        InMemoryTtlCache<String, PodcastByFeedIdRes>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val categoriesCache =
        InMemoryTtlCache<Unit, CategoryRes>(Duration.INFINITE, clock)

    val getTrendingPodcasts = GetTrendingPodcasts(api, blacklistRepository, trendingCache)
    val getLiveEpisodes = GetLiveEpisodes(api, blacklistRepository)
    val getRecentFeeds = GetRecentFeeds(api, blacklistRepository)
    val getMusicFeeds = GetMusicFeeds(api, blacklistRepository)
    val searchPodcasts = SearchPodcasts(api, blacklistRepository)
    val searchMusicFeeds = SearchMusicFeeds(api, blacklistRepository)
    val getCategories = GetCategories(api, categoriesCache, json)
    val getEpisodeByFeedId = GetEpisodeByFeedId(api, episodesByFeedCache)
    val getPodcastByFeedID = GetPodcastByFeedID(api, podcastByFeedCache)

    /** Releases the HTTP client. The graph cannot be used afterwards. */
    fun close() {
        httpClient.close()
    }
}
