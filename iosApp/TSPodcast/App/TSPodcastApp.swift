import SwiftUI
@preconcurrency import Shared

@main
struct TSPodcastApp: App {
    /// One graph per process: it owns the HTTP client, the blacklist and the in-memory caches.
    @State private var trending: TrendingDebugModel

    init() {
        let graph = SharedGraph(credentials: BundleCredentialsProvider(), config: NoOpConfig())
        _trending = State(initialValue: TrendingDebugModel(graph: graph))
    }

    var body: some Scene {
        WindowGroup {
            TrendingDebugView(model: trending)
        }
    }
}
