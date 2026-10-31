import SwiftUI

@main
struct TSPodcastApp: App {
    /// Owns the one `AppEnvironment` (and so the single `SharedGraph`) and receives the APNs token.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                // Universal Links (M4 §4.5): both entry points feed the same router, which ignores
                // every link while `APP_LINK_HOST` is empty.
                .onOpenURL { url in
                    let router = appDelegate.environment.deepLinks
                    Task { await router.handle(url) }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    guard let url = activity.webpageURL else { return }
                    let router = appDelegate.environment.deepLinks
                    Task { await router.handle(url) }
                }
                .environment(appDelegate.environment)
                .environment(\.artworkLoader, appDelegate.environment.artworkLoader)
                .tint(Color.brandPrimary)
        }
    }
}
