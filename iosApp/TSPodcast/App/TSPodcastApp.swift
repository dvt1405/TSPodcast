import SwiftUI

@main
struct TSPodcastApp: App {
    /// Created once per process; owns the single `SharedGraph`.
    @State private var environment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .environment(\.artworkLoader, environment.artworkLoader)
                .tint(Color.brandPrimary)
        }
    }
}
