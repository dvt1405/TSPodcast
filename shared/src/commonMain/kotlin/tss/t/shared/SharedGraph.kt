package tss.t.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.config.ConfigAPI
import tss.t.shared.detail.PodcastDetailFeed
import tss.t.shared.home.HomeFeed
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
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.preferences.DataStoreKeyValueStore
import tss.t.shared.preferences.FavouriteCategories
import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.search.SearchFeed
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
 * per process and keep it: it owns the `HttpClient`, the blacklist, the in-memory caches and
 * the key-value store, and DataStore allows only one open instance per file.
 *
 * The blacklist is loaded synchronously while the graph is built, so the first request
 * made through it is already filtered.
 *
 * From Swift: `SharedGraph(credentials: BundleCredentialsProvider(), config: NoOpConfig(),
 * preferencesPath: <Application Support>/ts_prefs.preferences_pb)`.
 */
class SharedGraph(
    credentials: CredentialsProvider,
    config: ConfigAPI,
    engine: HttpClientEngine,
    clock: EpochClock,
    private val store: KeyValueStore,
) {
    /**
     * Platform engine (OkHttp / Darwin), the system clock and a DataStore file at
     * [preferencesPath] (see [DataStoreKeyValueStore.FILE_NAME]).
     */
    constructor(credentials: CredentialsProvider, config: ConfigAPI, preferencesPath: String) :
        this(credentials, config, defaultHttpClientEngine(), EpochClock.System, DataStoreKeyValueStore(preferencesPath))

    private val json: Json = PodcastIndexJson

    private val httpClient: HttpClient =
        createHttpClient(engine, PodcastIndexAuth(credentials, clock), json)

    private val api = PodcastIndexApi(httpClient, credentials, json)

    val blacklistRepository: BlacklistRepository = BlacklistRepository(config, json)

    /** Onboarding flags, favourite categories, disk caches and the D6 flag. */
    val appPreferences: AppPreferences = AppPreferences(store, json)
    val favouriteCategories: FavouriteCategories = FavouriteCategories(appPreferences)

    private val trendingCache =
        InMemoryTtlCache<String, TrendingPodcastRes>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val episodesByFeedCache =
        InMemoryTtlCache<String, EpisodeResponse>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val podcastByFeedCache =
        InMemoryTtlCache<String, PodcastByFeedIdRes>(InMemoryTtlCache.API_CACHE_TTL, clock)
    private val categoriesCache =
        InMemoryTtlCache<Unit, CategoryRes>(Duration.INFINITE, clock)

    val getTrendingPodcasts = GetTrendingPodcasts(api, blacklistRepository, trendingCache, appPreferences, clock)
    val getLiveEpisodes = GetLiveEpisodes(api, blacklistRepository)
    val getRecentFeeds = GetRecentFeeds(api, blacklistRepository)
    val getMusicFeeds = GetMusicFeeds(api, blacklistRepository)
    val searchPodcasts = SearchPodcasts(api, blacklistRepository)
    val searchMusicFeeds = SearchMusicFeeds(api, blacklistRepository)
    val getCategories = GetCategories(api, categoriesCache, appPreferences, json)
    val getEpisodeByFeedId = GetEpisodeByFeedId(api, episodesByFeedCache)
    val getPodcastByFeedID = GetPodcastByFeedID(api, podcastByFeedCache)

    /** Every call behind Home/Discover (M1 §4.2). */
    val homeFeed = HomeFeed(
        getTrendingPodcasts = getTrendingPodcasts,
        getMusicFeeds = getMusicFeeds,
        getLiveEpisodes = getLiveEpisodes,
        getRecentFeeds = getRecentFeeds,
        preferences = appPreferences,
        clock = clock,
    )

    /** Podcast detail: E10 ‖ E11, E10 decides, D6 (M2 §4.1). */
    val podcastDetailFeed = PodcastDetailFeed(
        getEpisodeByFeedId = getEpisodeByFeedId,
        getPodcastByFeedID = getPodcastByFeedID,
        preferences = appPreferences,
    )

    /** Search tab: E7/E8 with D6, and trending per category (M2 §4.1). */
    val searchFeed = SearchFeed(
        searchPodcasts = searchPodcasts,
        searchMusicFeeds = searchMusicFeeds,
        getTrendingPodcasts = getTrendingPodcasts,
        preferences = appPreferences,
        clock = clock,
    )

    /** Releases the HTTP client and the store file. The graph cannot be used afterwards. */
    fun close() {
        httpClient.close()
        (store as? DataStoreKeyValueStore)?.close()
    }
}
