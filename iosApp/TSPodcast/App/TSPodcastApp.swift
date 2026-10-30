import SwiftUI

@main
struct TSPodcastApp: App {
    /// Owns the one `AppEnvironment` (and so the single `SharedGraph`) and receives the APNs token.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appDelegate.environment)
                .environment(\.artworkLoader, appDelegate.environment.artworkLoader)
                .tint(Color.brandPrimary)
        }
    }
}
