import Foundation
import Observation
@preconcurrency import Shared

/// Composition root of the app: one `SharedGraph` per process (it owns the HTTP client, the
/// caches and the DataStore file, and DataStore allows one open instance per file), plus the
/// Swift-side services. Injected with `.environment(_:)`; views never create a graph.
@MainActor
@Observable
final class AppEnvironment {
    @ObservationIgnored let graph: SharedGraph
    @ObservationIgnored let analytics: any AnalyticsTracker
    @ObservationIgnored let artworkLoader: ArtworkLoader
    @ObservationIgnored let launchOptions: LaunchOptions
    /// E9 list of this process, prefetched by onboarding and read by the category screen.
    @ObservationIgnored let categoryCatalog: CategoryCatalog
    /// The one player of the app (AVFoundation, M3).
    @ObservationIgnored let audioEngine: AudioEngine
    /// Whether the full-screen Player is shown (Detail, Live and the MiniPlayer open it).
    var isPlayerPresented = false
    /// Shared by every Detail screen of the process (the fail-once hook counts per process).
    @ObservationIgnored let podcastDetailDataSource: any PodcastDetailDataSource
    /// Search and category lists over shared `SearchFeed`.
    @ObservationIgnored let searchDataSource: any SearchDataSource

    var preferences: AppPreferences { graph.appPreferences }
    var favouriteCategories: FavouriteCategories { graph.favouriteCategories }

    init(launchOptions: LaunchOptions = .current, artworkLoader: ArtworkLoader = .shared) {
        self.launchOptions = launchOptions
        self.artworkLoader = artworkLoader
        let credentials = BundleCredentialsProvider(baseUrlOverride: launchOptions.baseURLOverride)
        if !credentials.isComplete {
            AppLog.app.error("missing_credentials")
        }
        graph = SharedGraph(
            credentials: credentials,
            config: NoOpConfig(),
            preferencesPath: Self.preferencesPath()
        )
        analytics = LogAnalyticsTracker()
        categoryCatalog = CategoryCatalog(getCategories: graph.getCategories)
        audioEngine = AudioEngine(
            positions: graph.playbackPositions,
            history: graph.playbackHistory,
            favourites: graph.favouriteStore,
            hooks: Self.audioHooks(launchOptions)
        )
        podcastDetailDataSource = SharedPodcastDetailDataSource(
            feed: graph.podcastDetailFeed,
            hooks: Self.detailHooks(launchOptions)
        )
        searchDataSource = SharedSearchDataSource(
            feed: graph.searchFeed,
            delay: launchOptions.slowNetwork ? HomeFeedDataSource.slowNetworkDelay : nil
        )
    }

    func makeOnboardingModel() -> OnboardingModel {
        let preferences = preferences
        return OnboardingModel(analytics: analytics) {
            try await preferences.saveOnboardingFinished(isFinished: true)
        }
    }

    func makeCategorySelectionModel() -> CategorySelectionModel {
        let catalog = categoryCatalog
        let favourites = favouriteCategories
        return CategorySelectionModel(
            minimumSelection: Int(FavouriteCategories.companion.MIN_FAVOURITE_CATEGORIES),
            load: { await catalog.categories() },
            save: { selection in
                try await favourites.save(categories: selection.map(\.sharedCategory)).boolValue
            }
        )
    }

    /// Home/Discover over shared `HomeFeed`. `-uitest-slow-network` (Debug) delays every call.
    func makeHomeModel() -> HomeModel {
        let dataSource = HomeFeedDataSource(
            feed: graph.homeFeed,
            favourites: favouriteCategories,
            delay: launchOptions.slowNetwork ? HomeFeedDataSource.slowNetworkDelay : nil
        )
        return HomeModel(dataSource: dataSource)
    }

    /// The player seam the screens use.
    var playback: any PlaybackController { audioEngine }

    /// Plays `request` and opens the Player (Detail tap, Home Live tap).
    func play(_ request: PlaybackRequest) {
        audioEngine.play(request)
        isPlayerPresented = true
    }

    /// Podcast detail of `route`, over shared `PodcastDetailFeed` and the app's player seam.
    func makePodcastDetailModel(for route: PodcastRoute) -> PodcastDetailModel {
        PodcastDetailModel(podcast: route, dataSource: podcastDetailDataSource, playback: playback)
    }

    /// The Search tab (debounce 200 ms on the continuous clock). `MainTabView` keeps it, so the
    /// query, the mode and the results survive tab switches.
    func makeSearchModel() -> SearchModel {
        SearchModel(dataSource: searchDataSource)
    }

    /// Podcasts trending in `category`, pushed from a Search category chip.
    func makeCategoryPodcastsModel(for category: CategoryItem) -> CategoryPodcastsModel {
        CategoryPodcastsModel(category: category, dataSource: searchDataSource)
    }

    private static func audioHooks(_ options: LaunchOptions) -> AudioEngine.Hooks {
        AudioEngine.Hooks(audioURLOverride: options.audioURLOverride, forceLive: options.forceLive)
    }

    private static func detailHooks(_ options: LaunchOptions) -> SharedPodcastDetailDataSource.TestHooks {
        SharedPodcastDetailDataSource.TestHooks(
            delay: options.slowNetwork ? HomeFeedDataSource.slowNetworkDelay : nil,
            failFirstLoad: options.failDetailOnce,
            empty: options.detailEmpty,
            explicitFeed: options.detailExplicit
        )
    }

    /// First screen of this launch (`TSNavGraph` order, logic in shared). Debug builds apply the
    /// XCUITest hooks first.
    func resolveStartRoute() async -> AppRoute {
        #if DEBUG
        await applyTestHooks()
        #endif
        do {
            let route = AppRoute(try await preferences.startDestination())
            AppLog.app.notice("start_destination=\(route.logName, privacy: .public)")
            return route
        } catch {
            // Only cancellation reaches here (the store never throws); onboarding is the safe start.
            AppLog.app.error("start_destination_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return .onboarding
        }
    }

    #if DEBUG
    /// `LaunchOptions.shouldReset` / `skipOnboarding`. Writes go through the same shared APIs
    /// as the real screens.
    private func applyTestHooks() async {
        guard launchOptions.shouldReset else { return }
        do {
            try await preferences.clear()
            await artworkLoader.removeAll()
            AppLog.app.notice("test_hook reset")
            guard launchOptions.skipOnboarding else { return }
            try await preferences.saveOnboardingFinished(isFinished: true)
            let categories = [(1, "Arts"), (2, "Books"), (3, "Design")].map { id, name in
                CategoryRes.Category(id: Int32(id), name: name, isFavourite: nil)
            }
            let saved = try await favouriteCategories.save(categories: categories).boolValue
            AppLog.app.notice("test_hook skip_onboarding saved=\(saved, privacy: .public)")
        } catch {
            AppLog.app.error("test_hook_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }
    #endif

    /// `Application Support/ts_prefs.preferences_pb`; the directory is created when missing.
    private static func preferencesPath() -> String {
        let directory = URL.applicationSupportDirectory
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        } catch {
            AppLog.app.error("preferences_dir_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
        return directory.appending(path: DataStoreKeyValueStore.companion.FILE_NAME).path(percentEncoded: false)
    }
}
