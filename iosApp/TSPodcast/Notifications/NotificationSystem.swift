import UIKit
import UserNotifications
@preconcurrency import Shared

/// Notification permission as the app needs it.
enum NotificationAuthorization: Equatable, Sendable {
    case notDetermined
    case denied
    /// Authorized, provisional or ephemeral: alerts can be delivered.
    case authorized
}

/// The system side of push opt-in: permission, APNs registration, the Settings app. Faked in
/// `NotificationsModelTests`.
@MainActor
protocol NotificationSystem: AnyObject {
    func authorization() async -> NotificationAuthorization
    /// The system permission dialog (only the first time; later it answers at once).
    func requestAuthorization() async -> Bool
    func registerForRemoteNotifications()
    /// `UIApplication.openSettingsURLString`.
    func openSettings()
}

/// `NotificationSystem` on `UNUserNotificationCenter` and `UIApplication`.
@MainActor
final class SystemNotifications: NotificationSystem {
    private var center: UNUserNotificationCenter { .current() }

    func authorization() async -> NotificationAuthorization {
        switch await center.notificationSettings().authorizationStatus {
        case .notDetermined: .notDetermined
        case .denied: .denied
        case .authorized, .provisional, .ephemeral: .authorized
        @unknown default: .denied
        }
    }

    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            AppLog.notifications.error("authorization_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return false
        }
    }

    func registerForRemoteNotifications() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

/// The two notification flags of shared `AppPreferences` (M4 §4.2), behind a protocol so tests
/// can count writes.
protocol NotificationPreferences: Sendable {
    func optIn() async -> Bool
    func setOptIn(_ value: Bool) async
    func promptShown() async -> Bool
    func setPromptShown(_ value: Bool) async
}

/// `NotificationPreferences` on shared `AppPreferences` (`NotificationsOptIn`,
/// `NotificationsPromptShown`). A failed read counts as false (nothing asked, nothing subscribed).
struct SharedNotificationPreferences: NotificationPreferences, @unchecked Sendable {
    let preferences: AppPreferences

    func optIn() async -> Bool {
        (try? await preferences.notificationsOptIn().boolValue) ?? false
    }

    func setOptIn(_ value: Bool) async {
        do {
            try await preferences.saveNotificationsOptIn(optIn: value)
        } catch {
            AppLog.notifications.error("save_opt_in_failed")
        }
    }

    func promptShown() async -> Bool {
        (try? await preferences.notificationsPromptShown().boolValue) ?? false
    }

    func setPromptShown(_ value: Bool) async {
        do {
            try await preferences.saveNotificationsPromptShown(shown: value)
        } catch {
            AppLog.notifications.error("save_prompt_shown_failed")
        }
    }
}
