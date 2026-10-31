import UIKit

/// UIKit callbacks SwiftUI has no API for (M4 §4.3): the APNs device token for FCM and the
/// notification delegate, which must be set before launch finishes so a tap that launched the
/// app is delivered.
@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Created on first use (by `didFinishLaunching` or the first `body`), once per process.
    private(set) lazy var environment = AppEnvironment()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        environment.didFinishLaunching()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        environment.services.push.didRegister(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        AppLog.notifications.error("apns_register_failed code=\((error as NSError).code, privacy: .public)")
    }
}
