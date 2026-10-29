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
