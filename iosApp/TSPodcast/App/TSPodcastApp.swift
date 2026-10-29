import SwiftUI
@preconcurrency import Shared

@main
struct TSPodcastApp: App {
    /// One graph per process: it owns the HTTP client, the blacklist, the caches and the
    /// key-value store file (DataStore allows a single open instance per file).
    @State private var trending: TrendingDebugModel

    init() {
        let graph = SharedGraph(
            credentials: BundleCredentialsProvider(),
            config: NoOpConfig(),
            preferencesPath: Self.preferencesPath()
        )
        _trending = State(initialValue: TrendingDebugModel(graph: graph))
    }

    /// `Application Support/ts_prefs.preferences_pb`; the directory is created when missing.
    private static func preferencesPath() -> String {
        let directory = URL.applicationSupportDirectory
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        } catch {
            AppLog.m0.error("preferences_dir_failed \(String(describing: type(of: error)), privacy: .public)")
        }
        return directory.appending(path: DataStoreKeyValueStore.companion.FILE_NAME).path(percentEncoded: false)
    }

    var body: some Scene {
        WindowGroup {
            TrendingDebugView(model: trending)
        }
    }
}
