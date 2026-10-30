package tss.t.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.HiddenShowsRepository
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.config.ConfigAPI
import tss.t.shared.crash.Crash
import tss.t.shared.crash.CrashReporter
import tss.t.shared.deeplink.DeepLinkTargets
import tss.t.shared.detail.PodcastDetailFeed
import tss.t.shared.detail.ShowEpisodeSearch
import tss.t.shared.favourite.FavouriteRepository
import tss.t.shared.favourite.FavouriteResolver
import tss.t.shared.home.HomeFeed
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.EpisodeResponse
import tss.t.shared.model.PodcastByFeedIdRes
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.newshub.NewsHubConfigRepository
import tss.t.shared.newshub.NewsHubRepository
import tss.t.shared.newshub.NewsSourceResolver
import tss.t.shared.network.CredentialsProvider
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.network.PodcastIndexAuth
import tss.t.shared.network.PodcastIndexJson
import tss.t.shared.network.createHttpClient
import tss.t.shared.network.defaultHttpClientEngine
import tss.t.shared.playback.PlaybackHistory
import tss.t.shared.playback.PlaybackPositions
import tss.t.shared.playback.PlaybackProfileRepository
import tss.t.shared.playback.SleepTimer
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.preferences.DataStoreKeyValueStore
import tss.t.shared.preferences.FavouriteCategories
import tss.t.shared.preferences.InstallId
import tss.t.shared.preferences.KeyValueStore
import tss.t.shared.privacy.ConsentRepository
import tss.t.shared.search.SearchFeed
import tss.t.shared.storage.PodcastDaos
import tss.t.shared.storage.PodcastDatabase
import tss.t.shared.storage.PodcastStorage
import tss.t.shared.storage.openPodcastDatabase
import tss.t.shared.usecase.GetCategories
import tss.t.shared.usecase.GetEpisodeByFeedId
import tss.t.shared.usecase.GetEpisodeById
import tss.t.shared.usecase.GetLiveEpisodes
import tss.t.shared.usecase.GetMusicFeeds
import tss.t.shared.usecase.GetPodcastByFeedID
import tss.t.shared.usecase.GetRecentFeeds
import tss.t.shared.usecase.GetTrendingPodcasts
import tss.t.shared.usecase.PruneApiCache
import tss.t.shared.usecase.SearchMusicFeeds
import tss.t.shared.usecase.SearchPodcasts
import tss.t.shared.util.EpochClock
import kotlin.time.Duration

/**
 * Hand-written composition root of the shared module (M0 §3.6; no Koin yet). Create **one**
 * per process and keep it: it owns the `HttpClient`, the blacklist, the in-memory caches, the
 * key-value store and the Room database, and DataStore allows only one open instance per file.
 *
 * The blacklist is loaded synchronously while the graph is built, so the first request
 * made through it is already filtered.
 *
 * From Swift: `SharedGraph(credentials: BundleCredentialsProvider(), config: NoOpConfig(),
 * preferencesPath: <Application Support>/ts_prefs.preferences_pb,
 * databasePath: <Application Support>/TSPodcastDatabase.db)`, plus `includeUnconfirmedNews:` for
 * the Debug news hub hook.
 *
 * @param includeUnconfirmedNews keeps news hub sources without `confirmedAt` (M6 §3.4). Only the
 *   Debug launch argument `-newshub-include-unconfirmed` sets it; Release always passes false.
 */
class SharedGraph(
    credentials: CredentialsProvider,
    config: ConfigAPI,
    engine: HttpClientEngine,
    clock: EpochClock,
    private val store: KeyValueStore,
    private val database: PodcastDaos,
    crash: CrashReporter = Crash,
    includeUnconfirmedNews: Boolean = false,
) {
    /**
     * Platform engine (OkHttp / Darwin), the system clock, a DataStore file at [preferencesPath]
     * (see [DataStoreKeyValueStore.FILE_NAME]) and the Room database at [databasePath]
     * (see [PodcastDatabase.FILE_NAME]; iOS only). The database opens on its first query.
     */
    constructor(
        credentials: CredentialsProvider,
        config: ConfigAPI,
        preferencesPath: String,
        databasePath: String,
    ) : this(credentials, config, preferencesPath, databasePath, includeUnconfirmedNews = false)

    /** Same as above; a separate constructor because Swift does not see Kotlin default values. */
    constructor(
        credentials: CredentialsProvider,
        config: ConfigAPI,
        preferencesPath: String,
        databasePath: String,
        includeUnconfirmedNews: Boolean,
    ) : this(
        credentials,
        config,
        defaultHttpClientEngine(),
        EpochClock.System,
        DataStoreKeyValueStore(preferencesPath),
        openPodcastDatabase(databasePath),
        Crash,
        includeUnconfirmedNews,
    )

    private val json: Json = PodcastIndexJson

    private val httpClient: HttpClient =
        createHttpClient(engine, PodcastIndexAuth(credentials, clock), json)

    private val api = PodcastIndexApi(httpClient, credentials, json)

    val blacklistRepository: BlacklistRepository = BlacklistRepository(config, json)

    /** Onboarding flags, favourite categories, disk caches and the D6 flag. */
    val appPreferences: AppPreferences = AppPreferences(store, json)
    val favouriteCategories: FavouriteCategories = FavouriteCategories(appPreferences)

    /** Shows the user hid, dropped by Home, Search and the news hub (M6 §3.5). */
    val hiddenShows: HiddenShowsRepository = HiddenShowsRepository(store, json, clock)

    /** Analytics and crash report consent, version and time (M6 §3.6). */
    val consentRepository: ConsentRepository = ConsentRepository(store, clock)

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
    val getMusicFeeds = GetMusicFeeds(api, blacklistRepository, crash)
    val searchPodcasts = SearchPodcasts(api, blacklistRepository)
    val searchMusicFeeds = SearchMusicFeeds(api, blacklistRepository)
    val getCategories = GetCategories(api, categoriesCache, appPreferences, json)
    val getEpisodeByFeedId = GetEpisodeByFeedId(api, episodesByFeedCache)
    val getPodcastByFeedID = GetPodcastByFeedID(api, podcastByFeedCache)

    /** E12, for episode deep links (M4 §4.2). */
    val getEpisodeById = GetEpisodeById(api)

    /** Android `getOrCreateInstallId()`: the crash reporter's user id (M4 §3.4). */
    val installId = InstallId(store)

    /** Podcast/Episode/Feed tables, written with `cachedAt = now` (M4 §3.1). */
    val podcastStorage = PodcastStorage(database, clock)

    /** Run once per launch, off the main thread (features-spec §1). */
    val pruneApiCache = PruneApiCache(database, clock, crash)

    /** Every call behind Home/Discover (M1 §4.2). */
    val homeFeed = HomeFeed(
        getTrendingPodcasts = getTrendingPodcasts,
        getMusicFeeds = getMusicFeeds,
        getLiveEpisodes = getLiveEpisodes,
        getRecentFeeds = getRecentFeeds,
        preferences = appPreferences,
        hiddenShows = hiddenShows,
        clock = clock,
    )

    /** Podcast detail: E10 ‖ E11, E10 decides, D6 (M2 §4.1). */
    val podcastDetailFeed = PodcastDetailFeed(
        getEpisodeByFeedId = getEpisodeByFeedId,
        getPodcastByFeedID = getPodcastByFeedID,
        preferences = appPreferences,
        storage = podcastStorage,
    )

    /** "Tìm trong show": up to 1 000 episodes, own cache entry, D6 (M6 §3.3). */
    val showEpisodeSearch = ShowEpisodeSearch(
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
        hiddenShows = hiddenShows,
        clock = clock,
    )

    /** Listening position per episode, in the app's key-value store (M3 §4.3). */
    val playbackPositions = PlaybackPositions(store)

    /** Playback speed per show, global and "apply to all" (M6 §3.2). */
    val playbackProfiles = PlaybackProfileRepository(store)

    /** The one sleep timer of the process, driven by the player (M6 §3.1). */
    val sleepTimer = SleepTimer(clock)

    /** `extra:current_item_id` plus the playlist and podcast in Room (M4 §3.2). */
    val playbackHistory = PlaybackHistory(store, podcastStorage)

    /** The `FavouriteDTO` table, observable (M4 §4.2). */
    val favouriteRepository = FavouriteRepository(database.favouriteDao())

    /** Favourite row → podcast, sorted episodes and start index (M4 §3.3). */
    val favouriteResolver = FavouriteResolver(podcastStorage)

    /** What a podcast or episode Universal Link opens, after the blacklist and D6 (M4 §4.5). */
    val deepLinkTargets = DeepLinkTargets(
        getEpisodeById = getEpisodeById,
        getPodcastByFeedID = getPodcastByFeedID,
        blacklist = blacklistRepository,
        preferences = appPreferences,
        storage = podcastStorage,
    )

    /** "Thời sự & Bản tin" configuration: Remote Config `news_hub_v1` or the bundled file (M6 §3.4). */
    val newsHubConfig: NewsHubConfigRepository =
        NewsHubConfigRepository(config, includeUnconfirmed = includeUnconfirmedNews)

    /** One per process: it reports each unresolved source once. */
    private val newsSourceResolver = NewsSourceResolver(api, store, clock, blacklistRepository, json, crash)

    /** The news hub, morning brief and night shelf (M6 §3.4). */
    val newsHub = NewsHubRepository(
        config = newsHubConfig,
        resolver = newsSourceResolver,
        getEpisodeByFeedId = getEpisodeByFeedId,
        preferences = appPreferences,
        hiddenShows = hiddenShows,
        blacklist = blacklistRepository,
    )

    /**
     * Reads every Remote Config value shared caches again (blacklist and news hub), after
     * Remote Config activated new values.
     */
    fun reloadRemoteConfig() {
        blacklistRepository.reload()
        newsHubConfig.reload()
    }

    /** Releases the HTTP client, the store file and the database. The graph cannot be used afterwards. */
    fun close() {
        httpClient.close()
        (store as? DataStoreKeyValueStore)?.close()
        (database as? PodcastDatabase)?.close()
    }
}
