import Foundation
@preconcurrency import Shared

/// "Báo cáo nội dung" (M6 §3.5): builds the `ContentReportMail` for the configured address, asks
/// the caller to open it and logs `content_report` (`kind` only, never the address) once a mail
/// app took it. When nothing can open `mailto:` (no Mail account) the caller shows the address
/// with a copy button instead.
///
/// `recipient` comes from Info.plist `TSContentReportEmail` (`CONTENT_REPORT_EMAIL`); empty, every
/// report entry (Detail, Player, news hub) is hidden.
@MainActor
final class ContentReporter {
    let recipient: String?
    private let appVersion: String
    private let analytics: any AnalyticsTracker

    init(recipient: String?, appVersion: String, analytics: any AnalyticsTracker) {
        self.recipient = recipient
        self.appVersion = appVersion
        self.analytics = analytics
    }

    convenience init(info: [String: Any], analytics: any AnalyticsTracker) {
        self.init(
            recipient: ContentReportMail.recipient(from: info),
            appVersion: SettingsModel.version(from: info),
            analytics: analytics
        )
    }

    /// Whether report buttons are shown at all.
    var isAvailable: Bool { recipient != nil }

    /// Opens the report mail through `open` (SwiftUI `openURL` with its completion). `failed`
    /// receives the address when no app accepted the URL.
    func report(
        kind: ContentReportKind,
        feedId: Int64?,
        episodeId: Int64?,
        screen: String,
        open: (URL, @escaping @Sendable (Bool) -> Void) -> Void,
        failed: @escaping @MainActor @Sendable (String) -> Void
    ) {
        guard let recipient,
              let url = ContentReportMail.url(
                  to: recipient,
                  kind: kind,
                  feedId: feedId,
                  episodeId: episodeId,
                  appVersion: appVersion
              )
        else { return }
        open(url) { [weak self] accepted in
            Task { @MainActor in
                self?.opened(kind: kind, accepted: accepted, screen: screen, recipient: recipient, failed: failed)
            }
        }
    }

    private func opened(
        kind: ContentReportKind,
        accepted: Bool,
        screen: String,
        recipient: String,
        failed: @MainActor (String) -> Void
    ) {
        AppLog.app.notice("content_report kind=\(kind.rawValue, privacy: .public) opened=\(accepted, privacy: .public)")
        guard accepted else {
            failed(recipient)
            return
        }
        let events = AnalyticsEvents.shared
        analytics.trackEvent(name: events.CONTENT_REPORT, screen: screen, params: [events.PARAM_KIND: kind.rawValue])
    }
}
