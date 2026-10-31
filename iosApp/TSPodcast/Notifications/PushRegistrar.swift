import FirebaseMessaging
import Foundation

/// Topic of the only push type iOS shows (D4, features-spec §3.11). The server sends
/// `new_podcasts` to iOS through this topic (handoff).
enum PushTopic {
    static let newPodcasts = "new_podcasts"
}

/// Push registration seam (M4 §4.3): hands the APNs token to FCM and (un)subscribes topics.
protocol PushRegistrar: AnyObject, Sendable {
    /// From `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`.
    func didRegister(deviceToken: Data)
    func subscribe(topic: String) async
    func unsubscribe(topic: String) async
}

/// `PushRegistrar` without Firebase: logs what it would do.
final class LogPushRegistrar: PushRegistrar {
    func didRegister(deviceToken: Data) {
        AppLog.notifications.notice("push_registrar=log apns_token bytes=\(deviceToken.count, privacy: .public)")
    }

    func subscribe(topic: String) async {
        AppLog.notifications.notice("push_registrar=log subscribe topic=\(topic, privacy: .public)")
    }

    func unsubscribe(topic: String) async {
        AppLog.notifications.notice("push_registrar=log unsubscribe topic=\(topic, privacy: .public)")
    }
}

/// `PushRegistrar` on Firebase Messaging. FCM auto-init is off in Info.plist
/// (`FirebaseMessagingAutoInitEnabled = NO`): no FCM token exists until the user opts in.
/// A subscribe made before the APNs token arrived waits for it (FCM needs the token first).
final class FirebasePushRegistrar: PushRegistrar, @unchecked Sendable {
    private let lock = NSLock()
    private var hasAPNsToken = false
    private var pendingTopics: Set<String> = []

    private var messaging: Messaging { Messaging.messaging() }

    func didRegister(deviceToken: Data) {
        messaging.apnsToken = deviceToken
        let topics = lock.withLock {
            hasAPNsToken = true
            defer { pendingTopics = [] }
            return pendingTopics
        }
        AppLog.notifications.notice("push_registrar=fcm apns_token set pending=\(topics.count, privacy: .public)")
        for topic in topics {
            Task { await self.subscribe(topic: topic) }
        }
    }

    func subscribe(topic: String) async {
        messaging.isAutoInitEnabled = true
        let ready = lock.withLock {
            if !hasAPNsToken { pendingTopics.insert(topic) }
            return hasAPNsToken
        }
        guard ready else {
            AppLog.notifications.notice("push_registrar=fcm subscribe_pending topic=\(topic, privacy: .public)")
            return
        }
        do {
            try await messaging.subscribe(toTopic: topic)
            AppLog.notifications.notice("push_registrar=fcm subscribed topic=\(topic, privacy: .public)")
        } catch {
            AppLog.notifications.error("push_subscribe_failed topic=\(topic, privacy: .public) type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }

    func unsubscribe(topic: String) async {
        let ready = lock.withLock {
            pendingTopics.remove(topic)
            return hasAPNsToken
        }
        guard ready else { return }
        do {
            try await messaging.unsubscribe(fromTopic: topic)
            AppLog.notifications.notice("push_registrar=fcm unsubscribed topic=\(topic, privacy: .public)")
        } catch {
            AppLog.notifications.error("push_unsubscribe_failed topic=\(topic, privacy: .public) type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }
}
