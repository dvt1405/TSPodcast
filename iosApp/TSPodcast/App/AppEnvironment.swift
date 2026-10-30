import Foundation
import Observation
import UserNotifications
@preconcurrency import Shared

/// Composition root of the app: one `SharedGraph` per process (it owns the HTTP client, the
/// caches, the DataStore file and the Room database, and DataStore allows one open instance per
/// file), plus the
/// Swift-side services. Injected with `.environment(_:)`; views never create a graph.
@MainActor
@Observable
final class AppEnvironment {
    @ObservationIgnored let graph: SharedGraph
    /// Firebase-backed or log/no-op seams (M4 §4.3).
    @ObservationIgnored let services: AppServices
    @ObservationIgnored let firebase: FirebaseStatus
    /// Every event of the app: `services.analytics` behind the consent gate (M6 §3.6).
    @ObservationIgnored let analytics: any AnalyticsTracker
    /// The privacy choice: first-launch sheet, Settings toggles, gate and SDK switches (M6 §3.6).
    @ObservationIgnored let consent: ConsentController
    /// "Ẩn show này" and Settings → "Show đã ẩn" (M6 §3.5).
    @ObservationIgnored let hiddenShows: HiddenShowsModel
    /// "Báo cáo nội dung" by mail; hidden without `CONTENT_REPORT_EMAIL` (M6 §3.5).
    @ObservationIgnored let contentReporter: ContentReporter
    /// Push opt-in (M4 §4.4): the Player prompt and the Settings toggle.
    @ObservationIgnored let notifications: NotificationsModel
    /// `UNUserNotificationCenter` delegate (kept here: the center holds it weakly).
    @ObservationIgnored let notificationPresenter: NotificationPresenter
    /// Favourite count for the one-time notification prompt.
    @ObservationIgnored private var favouriteCountObservation: FavouriteObservation?
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
    /// Tab, Discover stack and Settings sheet of Main, which Universal Links drive too.
    @ObservationIgnored let navigation: MainNavigation
    /// Universal Links (`.onOpenURL` / `.onContinueUserActivity`), host from `TSAppLinkHost` (M4 §4.5).
    @ObservationIgnored let deepLinks: DeepLinkRouter

    var preferences: AppPreferences { graph.appPreferences }
    var favouriteCategories: FavouriteCategories { graph.favouriteCategories }

    /// The launch of Android `App.onCreate` (M4 §3.4), in this order: Firebase (only with
    /// `GoogleService-Info.plist`) → the four seams → `Crash.install` (behind the consent gate,
    /// M6 §3.6) + the Kotlin crash hook → the graph (DataStore and Room files in Application
    /// Support, M4 §3.1) → in the background Remote Config and `PruneApiCache`. The crash user id
    /// (Install ID) is sent once crash reports are allowed.
    convenience init(
        launchOptions: LaunchOptions = .current,
        artworkLoader: ArtworkLoader = .shared,
        launch: LaunchSteps = .live
    ) {
        let firebase = launch.configureFirebase()
        let services = launch.makeServices(firebase)
        let gate = ConsentGate()
        let crashReporter = ConsentGatedCrashReporter(inner: services.crashReporter, local: OSLogCrashReporter(), gate: gate)
        launch.installCrashReporter(crashReporter)
        let (graph, pruner) = launch.makeGraph(services.config, launchOptions)
        self.init(
            launchOptions: launchOptions,
            artworkLoader: artworkLoader,
            graph: graph,
            pruner: pruner,
            services: services,
            firebase: firebase,
            consentGate: gate,
            crashReporter: crashReporter
        )
    }

    /// Builds the app over `graph` and `services`, then starts off the main actor, without
    /// waiting (features-spec §1: nothing here blocks the first screen): the Remote Config fetch
    /// (with Firebase only) and `pruner`, once each. `consentGate` closes analytics and
    /// `crashReporter` (the one installed as `Crash`) until the saved consent is read.
    init(
        launchOptions: LaunchOptions,
        artworkLoader: ArtworkLoader,
        graph: SharedGraph,
        pruner: any LaunchCachePruning,
        services: AppServices = .offline(),
        firebase: FirebaseStatus = .disabled(.missingPlist),
        notificationSystem: (any NotificationSystem)? = nil,
        consentGate: ConsentGate = ConsentGate(),
        crashReporter: (any CrashReporter)? = nil
    ) {
        self.launchOptions = launchOptions
        self.artworkLoader = artworkLoader
        self.graph = graph
        self.services = services
        self.firebase = firebase
        let analytics = ConsentGatedAnalyticsTracker(inner: services.analytics, gate: consentGate)
        self.analytics = analytics
        let installId = graph.installId
        let crash = crashReporter ?? services.crashReporter
        consent = ConsentController(
            store: SharedConsentStore(repository: graph.consentRepository),
            gate: consentGate,
            collection: services.collection,
            onCrashReportsAllowed: {
                Task.detached(priority: .utility) {
                    await CrashWiring.setUserId(installId: installId, reporter: crash)
                }
            }
        )
        hiddenShows = HiddenShowsModel(store: SharedHiddenShowsStore(repository: graph.hiddenShows), analytics: analytics)
        contentReporter = ContentReporter(info: Bundle.main.infoDictionary ?? [:], analytics: analytics)
        notificationPresenter = NotificationPresenter(analytics: analytics)
        notifications = NotificationsModel(
            preferences: SharedNotificationPreferences(preferences: graph.appPreferences),
            system: notificationSystem ?? SystemNotifications(),
            push: services.push
        )
        categoryCatalog = CategoryCatalog(getCategories: graph.getCategories)
        audioEngine = AudioEngine(
            positions: graph.playbackPositions,
            history: graph.playbackHistory,
            favourites: graph.favouriteRepository,
            profiles: graph.playbackProfiles,
            sleepTimer: graph.sleepTimer,
            analytics: analytics,
            hooks: Self.audioHooks(launchOptions)
        )
        podcastDetailDataSource = SharedPodcastDetailDataSource(
            feed: graph.podcastDetailFeed,
            search: graph.showEpisodeSearch,
            hooks: Self.detailHooks(launchOptions)
        )
        searchDataSource = SharedSearchDataSource(
            feed: graph.searchFeed,
            delay: launchOptions.slowNetwork ? HomeFeedDataSource.slowNetworkDelay : nil
        )
        navigation = MainNavigation()
        deepLinks = DeepLinkRouter(
            hosts: AppLinkHost.hosts(from: Bundle.main.infoDictionary ?? [:]),
            dataSource: SharedDeepLinkDataSource(targets: graph.deepLinkTargets),
            playback: audioEngine,
            navigation: navigation
        )
        deepLinks.willReveal = { [weak self] in self?.isPlayerPresented = false }
        let favouriteCounter = SharedFavouriteDataSource(repository: graph.favouriteRepository, resolver: graph.favouriteResolver)
        let notifications = notifications
        favouriteCountObservation = favouriteCounter.observe { rows in
            Task { await notifications.favouritesChanged(count: rows.count) }
        }
        let blacklist = graph.blacklistRepository
        let remoteConfig = services.remoteConfig.map { source in
            RemoteConfigLoader(source: source, crash: crash, onActivated: { blacklist.reload() })
        }
        Task.detached(priority: .utility) {
            if let remoteConfig {
                Task.detached(priority: .utility) { await remoteConfig.run() }
            }
            await pruner.prune()
        }
    }

    /// Called from `application(_:didFinishLaunchingWithOptions:)`: the notification delegate and
    /// the saved push opt-in (never a permission request, M4-AC18).
    func didFinishLaunching() {
        UNUserNotificationCenter.current().delegate = notificationPresenter
        Task { await notifications.start() }
    }

    /// The minimal Settings sheet (M4 §4.4).
    func makeSettingsModel() -> SettingsModel {
        SettingsModel(
            explicit: SharedExplicitContentPreference(preferences: preferences),
            notifications: notifications,
            info: Bundle.main.infoDictionary ?? [:]
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
        let model = HomeModel(dataSource: dataSource)
        hiddenShows.onHide { [weak model] feedId in model?.removeShow(feedId: feedId) }
        return model
    }

    /// The player seam the screens use.
    var playback: any PlaybackController { audioEngine }

    /// Plays `request` and opens the Player (Detail tap, Home Live tap).
    func play(_ request: PlaybackRequest) {
        audioEngine.play(request)
        isPlayerPresented = true
    }

    /// The Favourite tab over the `FavouriteDTO` table: a tap plays through the player seam
    /// without opening the Player (Android `FavouriteScreen`).
    func makeFavouriteModel() -> FavouriteModel {
        FavouriteModel(
            dataSource: SharedFavouriteDataSource(repository: graph.favouriteRepository, resolver: graph.favouriteResolver),
            playback: playback
        )
    }

    /// Podcast detail of `route`, over shared `PodcastDetailFeed` and the app's player seam.
    func makePodcastDetailModel(for route: PodcastRoute) -> PodcastDetailModel {
        PodcastDetailModel(podcast: route, dataSource: podcastDetailDataSource, playback: playback, analytics: analytics)
    }

    /// The Search tab (debounce 200 ms on the continuous clock). `MainTabView` keeps it, so the
    /// query, the mode and the results survive tab switches.
    func makeSearchModel() -> SearchModel {
        let model = SearchModel(dataSource: searchDataSource)
        hiddenShows.onHide { [weak model] feedId in model?.removeShow(feedId: feedId) }
        return model
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
        // The saved consent opens or keeps closed the gate before the first screen sends anything.
        await consent.load()
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
    /// `LaunchOptions.shouldReset` / `skipOnboarding`, then `consentChoice`. Writes go through the
    /// same shared APIs as the real screens.
    private func applyTestHooks() async {
        await applyResetHooks()
        await applyConsentHook()
    }

    private func applyResetHooks() async {
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

    /// `-uitest-consent=all|necessary`: the decision is saved before routing (after a reset,
    /// which clears it), so the consent sheet does not cover the screens under test.
    private func applyConsentHook() async {
        guard let choice = launchOptions.consentChoice else { return }
        let repository = graph.consentRepository
        do {
            switch choice {
            case .all: _ = try await repository.acceptAll()
            case .necessary: _ = try await repository.necessaryOnly()
            }
            AppLog.app.notice("test_hook consent=\(choice.rawValue, privacy: .public)")
        } catch {
            AppLog.app.error("test_hook_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }
    #endif

    /// `Application Support/ts_prefs.preferences_pb`; the directory is created when missing.
    fileprivate static func preferencesPath() -> String {
        applicationSupportFile(DataStoreKeyValueStore.companion.FILE_NAME)
    }

    /// `DatabaseLocation.url` (M4 §3.1). Room opens it on the first query, which is the launch
    /// prune; the pruner then excludes it from backup.
    fileprivate static func databaseURL() -> URL {
        createApplicationSupportDirectory()
        return DatabaseLocation.url
    }

    private static func applicationSupportFile(_ name: String) -> String {
        createApplicationSupportDirectory()
        return URL.applicationSupportDirectory.appending(path: name).path(percentEncoded: false)
    }

    private static func createApplicationSupportDirectory() {
        do {
            try FileManager.default.createDirectory(at: .applicationSupportDirectory, withIntermediateDirectories: true)
        } catch {
            AppLog.app.error("app_support_dir_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }
}

/// The steps of the launch that touch process-wide state (Firebase, the shared `Crash`), so
/// `CrashWiringTests` can check their order with fakes. `.live` is the app's.
@MainActor
struct LaunchSteps {
    var configureFirebase: () -> FirebaseStatus
    var makeServices: (FirebaseStatus) -> AppServices
    /// `Crash.install` + `installKotlinCrashHook`, before any other shared call.
    var installCrashReporter: (any CrashReporter) -> Void
    /// The graph over the chosen `ConfigAPI`, and the launch cache pruner.
    var makeGraph: (any ConfigAPI, LaunchOptions) -> (SharedGraph, any LaunchCachePruning)

    static var live: LaunchSteps {
        LaunchSteps(
            configureFirebase: { FirebaseBootstrap.configureIfAvailable() },
            makeServices: { AppServices.make(for: $0) },
            installCrashReporter: { CrashWiring.install($0) },
            makeGraph: { config, options in
                let credentials = BundleCredentialsProvider(baseUrlOverride: options.baseURLOverride)
                if !credentials.isComplete {
                    AppLog.app.error("missing_credentials")
                }
                let databaseURL = AppEnvironment.databaseURL()
                let graph = SharedGraph(
                    credentials: credentials,
                    config: config,
                    preferencesPath: AppEnvironment.preferencesPath(),
                    databasePath: databaseURL.path(percentEncoded: false)
                )
                return (graph, SharedCachePruner(pruneApiCache: graph.pruneApiCache, databaseURL: databaseURL))
            }
        )
    }
}
