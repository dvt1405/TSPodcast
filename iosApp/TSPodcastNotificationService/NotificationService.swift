import Foundation
import UserNotifications

/// Notification Service Extension (M4 §4.4): adds the `big_image` of a `new_podcasts` push
/// (`mutable-content = 1`) as an attachment, through `RichNotification` (shared with the app
/// target). When time runs out the original content is delivered. No Firebase here.
final class NotificationService: UNNotificationServiceExtension {
    private let delivery = Delivery()
    private var work: Task<Void, Never>?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        let original = request.content
        delivery.prepare(handler: contentHandler, original: original)
        let delivery = delivery
        let content = UnsafeContent(value: original)
        work = Task {
            let enriched = await RichNotification().enrich(content.value)
            delivery.deliver(enriched)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        work?.cancel()
        delivery.deliverOriginal()
    }
}

/// Notification content handed to the download task. It is not changed while the task reads it.
private struct UnsafeContent: @unchecked Sendable {
    let value: UNNotificationContent
}

/// Calls the content handler exactly once, from whichever finishes first: the download or the
/// expiry callback.
private final class Delivery: @unchecked Sendable {
    private let lock = NSLock()
    private var handler: ((UNNotificationContent) -> Void)?
    private var original: UNNotificationContent?

    func prepare(handler: @escaping (UNNotificationContent) -> Void, original: UNNotificationContent) {
        lock.withLock {
            self.handler = handler
            self.original = original
        }
    }

    func deliver(_ content: UNNotificationContent) {
        let handler = lock.withLock {
            defer { self.handler = nil }
            return self.handler
        }
        handler?(content)
    }

    func deliverOriginal() {
        guard let original = lock.withLock({ self.original }) else { return }
        deliver(original)
    }
}
