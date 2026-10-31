import Foundation
@preconcurrency import Shared

/// `NewsHubDataSource` backed by shared's `NewsHubRepository` (config, source resolution through
/// PodcastIndex, 20 episodes per source, hidden shows, D6, blocklist, "Mới nhất" and the morning
/// brief all live in shared). This adapter maps Kotlin values to Swift structs, logs failures by
/// type name and reports each unresolved source once per process as `news_source_unresolved`.
///
/// `@unchecked Sendable`: immutable references to Kotlin objects, which any thread may call.
final class SharedNewsHubDataSource: NewsHubDataSource, @unchecked Sendable {
    private let repository: NewsHubRepository
    private let now: @Sendable () -> Date
    /// `-uitest-slow-network` (Debug only): wait this long before every load.
    private let delay: Duration?
    /// Kept: the repository holds its listener, this keeps the Swift side obvious.
    private let unresolvedReporter: UnresolvedSourceReporter

    init(
        repository: NewsHubRepository,
        analytics: any AnalyticsTracker,
        delay: Duration? = nil,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.repository = repository
        self.delay = delay
        self.now = now
        unresolvedReporter = UnresolvedSourceReporter(analytics: analytics)
        repository.unresolvedListener = unresolvedReporter
    }

    var isEnabled: Bool { repository.isEnabled }

    func load() async -> Result<NewsHubContent?, HomeLoadError> {
        if let delay {
            try? await Task.sleep(for: delay)
        }
        do {
            let nowMillis = Int64((now().timeIntervalSince1970 * 1000).rounded())
            let state = try await repository.load(nowMillis: nowMillis)
            if let hub = (state as? TSDataStateSuccess<NewsHub>)?.data {
                guard !hub.isEmpty else {
                    AppLog.browse.notice("news_hub_loaded enabled=\(hub.isEnabled, privacy: .public) empty=true")
                    return .success(nil)
                }
                let content = NewsHubContent(hub)
                AppLog.browse.notice(
                    "news_hub_loaded news=\(hub.news.count, privacy: .public) night=\(hub.night.count, privacy: .public) latest=\(content.latest.count, privacy: .public) brief=\(content.brief.count, privacy: .public)"
                )
                return .success(content.hasNews ? content : nil)
            }
            let reason = (state as? TSDataStateError<NewsHub>)?.exception.typeName ?? "UnexpectedState"
            return .failure(Self.failed(reason))
        } catch {
            // A Swift error out of a suspend call: only cancellation in practice.
            return .failure(Self.failed(String(describing: type(of: error))))
        }
    }

    private static func failed(_ reason: String) -> HomeLoadError {
        AppLog.browse.error("news_hub_failed type=\(reason, privacy: .public)")
        return HomeLoadError(reason: reason)
    }
}

/// Shared calls it once per process and source key (`NewsSourceResolver`), from any thread.
/// Sends only the config key, never a URL or a title.
private final class UnresolvedSourceReporter: NSObject, NewsSourceUnresolvedListener, @unchecked Sendable {
    private let analytics: any AnalyticsTracker

    init(analytics: any AnalyticsTracker) {
        self.analytics = analytics
    }

    func onUnresolved(sourceKey: String) {
        AppLog.browse.notice("news_source_unresolved key=\(sourceKey, privacy: .public)")
        let events = AnalyticsEvents.shared
        analytics.trackEvent(
            name: events.NEWS_SOURCE_UNRESOLVED,
            screen: nil,
            params: [events.PARAM_SOURCE_KEY: sourceKey]
        )
    }
}

extension NewsHubContent {
    init(_ hub: NewsHub) {
        self.init(
            brief: hub.brief.items.map(NewsItem.init),
            latest: hub.latest.map(NewsItem.init),
            sources: hub.news.map(NewsSourceCard.init),
            night: hub.night.flatMap { source in
                source.episodes.map { NewsItem(NewsEpisode(sourceKey: source.key, feed: source.feed, episode: $0)) }
            },
            nightSleepTimerMinutes: Int(hub.nightSleepTimerMinutes)
        )
    }
}

extension NewsItem {
    init(_ item: NewsEpisode) {
        let episode = item.episode
        let published = episode.datePublished
        self.init(
            id: episode.id,
            feedId: item.feed.id,
            sourceKey: item.sourceKey,
            title: episode.title,
            feedTitle: item.feed.title.trimmedNonBlank ?? episode.title,
            imageURL: item.feed.artworkURL ?? episode.getImageUrl().trimmedNonBlank,
            referer: episode.link.trimmedNonBlank,
            publishedAt: published > 0 ? Date(timeIntervalSince1970: TimeInterval(published)) : nil,
            durationSeconds: episode.duration,
            enclosureURL: episode.enclosureUrl,
            summary: HtmlText.shared.toPlainText(html: episode.description_),
            source: KotlinValue(item)
        )
    }
}

extension NewsSourceCard {
    init(_ source: ResolvedSource) {
        self.init(
            id: source.feed.id,
            key: source.key,
            title: source.feed.title.trimmedNonBlank ?? source.key,
            summary: HtmlText.shared.toPlainText(html: source.feed.description_),
            imageURL: source.feed.artworkURL
        )
    }
}

private extension Feed {
    /// `artwork`, else `image`; nil when both are blank.
    var artworkURL: String? {
        artwork.trimmedNonBlank ?? image.trimmedNonBlank
    }
}
