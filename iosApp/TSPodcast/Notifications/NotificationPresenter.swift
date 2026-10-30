import Foundation
import UserNotifications
@preconcurrency import Shared

/// `UNUserNotificationCenterDelegate` of the app (Android `TSFcmServices.onMessageReceived`):
///
/// - in the foreground only `type = new_podcasts` shows (banner, list, sound); the ad types of
///   Android (`banner_ads`, `video_ads`, `full_screen_ads`) and any unknown type stay hidden (D4);
/// - every notification the app sees logs `fcm_<type>_message_receive` with the payload's keys
///   (without `aps`), when it arrives in the foreground and when the user taps it. A push that
///   arrives in the background and is never tapped gives the app no callback, so it logs
///   nothing (plan §7 item 8);
/// - a tap only opens the app (Android's notification has no content intent).
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate, Sendable {
    static let typeKey = "type"

    private let analytics: any AnalyticsTracker

    init(analytics: any AnalyticsTracker) {
        self.analytics = analytics
    }

    /// Presentation of a notification arriving while the app is in the foreground.
    func willPresent(userInfo: [AnyHashable: Any]) -> UNNotificationPresentationOptions {
        logReceive(userInfo, phase: "foreground")
        return Self.presentationOptions(for: userInfo)
    }

    /// The user tapped a notification.
    func didReceive(userInfo: [AnyHashable: Any]) {
        logReceive(userInfo, phase: "tap")
    }

    static func presentationOptions(for userInfo: [AnyHashable: Any]) -> UNNotificationPresentationOptions {
        pushType(in: userInfo) == PushTopic.newPodcasts ? [.banner, .list, .sound] : []
    }

    /// `type` of the payload, or nil when missing or not a string.
    static func pushType(in userInfo: [AnyHashable: Any]) -> String? {
        userInfo[typeKey] as? String
    }

    /// `fcm_<type>_message_receive` and the payload's top-level keys except `aps`, values as text
    /// (Android sends the FCM data map).
    static func receiveEvent(for userInfo: [AnyHashable: Any]) -> (name: String, params: [String: String]) {
        var params: [String: String] = [:]
        for (key, value) in userInfo {
            guard let key = key as? String, key != "aps" else { continue }
            switch value {
            case let text as String: params[key] = text
            case let number as NSNumber: params[key] = number.stringValue
            default: continue
            }
        }
        return (AnalyticsDefaults.shared.fcmReceiveEvent(type: pushType(in: userInfo)), params)
    }

    private func logReceive(_ userInfo: [AnyHashable: Any], phase: String) {
        let event = Self.receiveEvent(for: userInfo)
        AppLog.notifications.notice("push_received phase=\(phase, privacy: .public) event=\(event.name, privacy: .public)")
        analytics.trackEvent(name: event.name, screen: nil, params: event.params)
    }

    // MARK: - UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        willPresent(userInfo: notification.request.content.userInfo)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        didReceive(userInfo: response.notification.request.content.userInfo)
    }
}
