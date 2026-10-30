import Foundation
import UserNotifications

/// Attaches the `big_image` of a push to its content (features-spec §3.11: the large image of
/// Android's `new_podcasts` notification).
///
/// Compiled into both the Notification Service Extension and the app target (so `TSPodcastTests`
/// test it). Foundation and UserNotifications only: no Shared, no Firebase, no app logger.
///
/// The image URL is `big_image`, else FCM's `fcm_options.image`; only an absolute http(s) URL with
/// a host counts. Any failure (no URL, bad URL, HTTP error, not an image, too slow) gives back the
/// original content, so the notification always shows.
struct RichNotification: Sendable {
    static let imageKey = "big_image"
    static let attachmentIdentifier = "big_image"
    /// The extension has about 30 s; the download gets clearly less.
    static let defaultTimeout: Duration = .seconds(20)

    let session: URLSession
    let timeout: Duration

    init(session: URLSession = .shared, timeout: Duration = RichNotification.defaultTimeout) {
        self.session = session
        self.timeout = timeout
    }

    /// The image to attach, or nil.
    static func imageURL(in userInfo: [AnyHashable: Any]) -> URL? {
        let fcmOptions = userInfo["fcm_options"] as? [AnyHashable: Any]
        let candidates = [userInfo[imageKey], fcmOptions?["image"]]
        for case let text as String in candidates {
            if let url = validURL(text) { return url }
        }
        return nil
    }

    static func validURL(_ text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              let host = url.host(), !host.isEmpty
        else { return nil }
        return url
    }

    /// `content` with the image attached, or `content` itself when there is nothing to attach.
    func enrich(_ content: UNNotificationContent) async -> UNNotificationContent {
        guard let url = Self.imageURL(in: content.userInfo),
              let mutable = content.mutableCopy() as? UNMutableNotificationContent,
              let file = await download(url)
        else { return content }
        do {
            let attachment = try UNNotificationAttachment(identifier: Self.attachmentIdentifier, url: file)
            mutable.attachments = [attachment]
            return mutable
        } catch {
            try? FileManager.default.removeItem(at: file)
            return content
        }
    }

    /// The image in a temporary file with an image extension, or nil on failure or timeout.
    private func download(_ url: URL) async -> URL? {
        let session = session
        let timeout = timeout
        return await withTaskGroup(of: URL?.self) { group in
            group.addTask { await Self.fetch(url, session: session) }
            group.addTask {
                try? await Task.sleep(for: timeout)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private static func fetch(_ url: URL, session: URLSession) async -> URL? {
        do {
            let (downloaded, response) = try await session.download(from: url)
            guard !Task.isCancelled,
                  let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let fileExtension = imageExtension(ofFileAt: downloaded)
            else {
                try? FileManager.default.removeItem(at: downloaded)
                return nil
            }
            let file = FileManager.default.temporaryDirectory
                .appending(path: "\(UUID().uuidString).\(fileExtension)")
            try FileManager.default.moveItem(at: downloaded, to: file)
            return file
        } catch {
            return nil
        }
    }

    /// The image type from the file's first bytes, as the extension `UNNotificationAttachment`
    /// needs; nil when the file is not a JPEG, PNG, GIF, WebP or HEIC image (an error page, …).
    /// The server's `Content-Type` is not trusted: CDNs often send `octet-stream`.
    static func imageExtension(ofFileAt url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        guard let header = try? handle.read(upToCount: 12) else { return nil }
        return imageExtension(header: header)
    }

    static func imageExtension(header: Data) -> String? {
        let bytes = [UInt8](header.prefix(12))
        func starts(_ prefix: [UInt8], at offset: Int = 0) -> Bool {
            bytes.count >= offset + prefix.count && Array(bytes[offset..<offset + prefix.count]) == prefix
        }
        if starts([0xFF, 0xD8, 0xFF]) { return "jpg" }
        if starts([0x89, 0x50, 0x4E, 0x47]) { return "png" }
        if starts(Array("GIF8".utf8)) { return "gif" }
        if starts(Array("RIFF".utf8)), starts(Array("WEBP".utf8), at: 8) { return "webp" }
        if starts(Array("ftyp".utf8), at: 4) { return "heic" }
        return nil
    }
}
