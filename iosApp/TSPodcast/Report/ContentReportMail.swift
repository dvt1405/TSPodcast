import Foundation

/// What a content report is about (M6 §3.5, N1 lite); the raw value is the `kind` line of the
/// mail and of the `content_report` event.
enum ContentReportKind: String, Sendable, CaseIterable {
    /// A whole show (Podcast detail).
    case feed
    /// One episode (Player).
    case episode
    /// The news hub (its footer).
    case hub
}

/// The pre-filled `mailto:` of "Báo cáo nội dung": the user sends it from their own mail app, so
/// nothing is stored or uploaded by the app (no backend database on iOS). Plain Swift, no Shared, so
/// `ContentReportMailTests` also run on macOS.
///
/// The subject names the kind and the ids; the body lists `kind`, `feedId`, `episodeId` (when
/// known) and the app version, then leaves room for the user's description. Subject and body
/// are percent-encoded per RFC 6068 (line breaks as `%0D%0A`, spaces as `%20`, never `+`).
enum ContentReportMail {
    /// Info.plist key of the report address (build setting `CONTENT_REPORT_EMAIL`).
    static let infoKey = "TSContentReportEmail"

    /// The report address of `info`, or nil when empty, unset (`$(…)` left by a missing build
    /// setting) or not an address: every report entry is then hidden.
    static func recipient(from info: [String: Any]) -> String? {
        guard let text = (info[infoKey] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              isAddress(text)
        else { return nil }
        return text
    }

    /// `mailto:` for a report, or nil when `to` is not an address.
    static func url(
        to recipient: String,
        kind: ContentReportKind,
        feedId: Int64?,
        episodeId: Int64?,
        appVersion: String
    ) -> URL? {
        let to = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isAddress(to), let encodedTo = to.addingPercentEncoding(withAllowedCharacters: addressAllowed) else {
            return nil
        }
        let query = [
            "subject=" + encode(subject(kind: kind, feedId: feedId, episodeId: episodeId)),
            "body=" + encode(body(kind: kind, feedId: feedId, episodeId: episodeId, appVersion: appVersion)),
        ].joined(separator: "&")
        return URL(string: "mailto:\(encodedTo)?\(query)")
    }

    /// "[TSPodcast] Báo cáo nội dung · episode 456 · feed 123".
    static func subject(kind: ContentReportKind, feedId: Int64?, episodeId: Int64?) -> String {
        var parts = ["[TSPodcast] Báo cáo nội dung / Content report", kind.rawValue]
        if let episodeId { parts.append("episode \(episodeId)") }
        if let feedId { parts.append("feed \(feedId)") }
        return parts.joined(separator: " · ")
    }

    /// The fixed lines the support team reads, then a blank line for the user.
    static func body(kind: ContentReportKind, feedId: Int64?, episodeId: Int64?, appVersion: String) -> String {
        var lines = ["kind: \(kind.rawValue)"]
        if let feedId { lines.append("feedId: \(feedId)") }
        if let episodeId { lines.append("episodeId: \(episodeId)") }
        lines.append("appVersion: \(appVersion)")
        lines.append("")
        lines.append("Mô tả vấn đề / What is wrong:")
        lines.append("")
        return lines.joined(separator: "\r\n")
    }

    /// RFC 3986 unreserved characters only: everything else, `+`, `&`, `=` and line breaks
    /// included, is percent-encoded.
    private static let unreserved = CharacterSet(charactersIn:
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

    private static let addressAllowed = unreserved.union(CharacterSet(charactersIn: "@+"))

    private static func encode(_ text: String) -> String {
        text.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }

    /// One `@` with something on both sides, no space, no `$(` placeholder.
    private static func isAddress(_ text: String) -> Bool {
        let parts = text.split(separator: "@", omittingEmptySubsequences: false)
        return parts.count == 2 && !parts[0].isEmpty && parts[1].contains(".")
            && !text.contains(where: \.isWhitespace) && !text.contains("$(")
            && !text.contains("?") && !text.contains("&")
    }
}
