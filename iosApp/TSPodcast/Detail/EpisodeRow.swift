import Foundation

/// An episode of Podcast detail as the UI and the player seam see it (Android `EpisodeWidget`
/// reads the Kotlin `Episode` directly; here views never hold Kotlin objects). Built from
/// shared's `Episode` in `SharedPodcastDetailDataSource`.
struct EpisodeRow: Identifiable, Hashable, Sendable {
    let id: Int64
    let title: String
    /// `description` as plain text (`HtmlText.toPlainText`), shown on 3 lines.
    let summary: String
    /// `Episode.getImageUrl()`: the episode image, else `feedImage`; nil when both are blank.
    let imageURL: String?
    /// `episode.link`, sent as `Referer` with the image request; nil when blank.
    let referer: String?
    /// `datePublished` (Unix seconds); nil when 0.
    let publishedAt: Date?
    /// `duration` in seconds; 0 or less means unknown.
    let durationSeconds: Int64
    /// Audio URL and MIME type, for the player (M3).
    let enclosureURL: String
    let enclosureType: String

    /// The line under the title: "date · duration", dropping a missing part and its " · "
    /// (features-spec §3.5 "ảnh, tiêu đề, ngày, thời lượng"). Empty when both are missing.
    func meta(locale: Locale = .autoupdatingCurrent, timeZone: TimeZone = .autoupdatingCurrent) -> String {
        [Self.date(publishedAt, locale: locale, timeZone: timeZone), Self.duration(durationSeconds, locale: locale)]
            .compactMap { $0 }
            .joined(separator: " · ")
    }

    /// Localized day, abbreviated month and year, e.g. "30 thg 10, 2026" / "Oct 30, 2026".
    static func date(_ date: Date?, locale: Locale, timeZone: TimeZone) -> String? {
        guard let date else { return nil }
        var style = Date.FormatStyle.dateTime.day().month(.abbreviated).year()
        style.locale = locale
        style.timeZone = timeZone
        return date.formatted(style)
    }

    /// Hours and minutes only, abbreviated ("45 phút", "1 giờ, 5 phút"); under a minute counts
    /// as one minute, seconds are rounded to the nearest minute; nil when `seconds <= 0`.
    static func duration(_ seconds: Int64, locale: Locale) -> String? {
        guard seconds > 0 else { return nil }
        let minutes = max(1, (seconds + 30) / 60)
        return Duration.seconds(minutes * 60)
            .formatted(.units(allowed: [.hours, .minutes], width: .abbreviated).locale(locale))
    }
}
