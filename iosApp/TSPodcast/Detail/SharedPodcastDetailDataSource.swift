import Foundation
@preconcurrency import Shared

/// `PodcastDetailDataSource` backed by shared's `PodcastDetailFeed`. This adapter only maps
/// Kotlin values to Swift structs and logs failures by type name.
///
/// `AppEnvironment` keeps one instance per process, so the "first load" of
/// `-uitest-fail-detail-once` is the first of the process.
///
/// `@unchecked Sendable`: immutable references to Kotlin objects (callable from any thread) plus
/// a lock-protected flag.
final class SharedPodcastDetailDataSource: PodcastDetailDataSource, @unchecked Sendable {
    /// Debug-only behaviours for XCUITest (M2 §4.6); all off in Release.
    struct TestHooks: Equatable, Sendable {
        /// `-uitest-slow-network`: wait this long before every call.
        var delay: Duration?
        /// `-uitest-fail-detail-once`: the first load fails without a request.
        var failFirstLoad = false
        /// `-uitest-detail-empty`: every load succeeds with no episode, without a request.
        var empty = false
        /// `-uitest-detail-explicit`: every load reports the feed hidden by D6, without a request.
        var explicitFeed = false

        static let none = TestHooks()
    }

    private let feed: PodcastDetailFeed
    private let hooks: TestHooks
    private let lock = NSLock()
    private var hasFailedOnce = false

    init(feed: PodcastDetailFeed, hooks: TestHooks = .none) {
        self.feed = feed
        self.hooks = hooks
    }

    func load(feedId: Int64) async -> Result<PodcastDetailContent, DetailLoadError> {
        if let delay = hooks.delay {
            try? await Task.sleep(for: delay)
        }
        if let canned = cannedResult() {
            return canned
        }
        do {
            let state = try await feed.load(feedId: feedId)
            if let detail = (state as? TSDataStateSuccess<PodcastDetail>)?.data {
                let content = PodcastDetailContent(
                    episodes: detail.episodes.map(EpisodeRow.init),
                    isExplicitFeedHidden: detail.isExplicitFeedHidden,
                    hiddenExplicitCount: Int(detail.hiddenExplicitCount),
                    isFromCache: detail.isFromCache,
                    source: KotlinValue(detail)
                )
                AppLog.browse.notice(
                    "detail_loaded feedId=\(feedId, privacy: .public) count=\(content.episodes.count, privacy: .public) hiddenExplicit=\(content.hiddenExplicitCount, privacy: .public) feedHidden=\(content.isExplicitFeedHidden, privacy: .public) fromCache=\(content.isFromCache, privacy: .public)"
                )
                return .success(content)
            }
            let reason = (state as? TSDataStateError<PodcastDetail>)?.exception.typeName ?? "UnexpectedState"
            return .failure(Self.failed(feedId: feedId, reason: reason))
        } catch {
            // A Swift error out of a suspend call: only cancellation in practice.
            return .failure(Self.failed(feedId: feedId, reason: String(describing: type(of: error))))
        }
    }

    /// Results of the Debug hooks, or nil to call shared.
    private func cannedResult() -> Result<PodcastDetailContent, DetailLoadError>? {
        #if DEBUG
        if hooks.failFirstLoad {
            let mustFail = lock.withLock {
                defer { hasFailedOnce = true }
                return !hasFailedOnce
            }
            if mustFail {
                AppLog.browse.notice("test_hook fail_detail_once")
                return .failure(DetailLoadError(reason: "TestHookFailure"))
            }
        }
        if hooks.explicitFeed {
            return .success(PodcastDetailContent(episodes: [], isExplicitFeedHidden: true, hiddenExplicitCount: 3))
        }
        if hooks.empty {
            return .success(PodcastDetailContent(episodes: []))
        }
        #endif
        return nil
    }

    private static func failed(feedId: Int64, reason: String) -> DetailLoadError {
        AppLog.browse.error("detail_failed feedId=\(feedId, privacy: .public) type=\(reason, privacy: .public)")
        return DetailLoadError(reason: reason)
    }
}

extension EpisodeRow {
    init(_ episode: Episode) {
        let published = episode.datePublished
        self.init(
            id: episode.id,
            title: episode.title,
            summary: HtmlText.shared.toPlainText(html: episode.description_),
            imageURL: episode.getImageUrl().trimmedNonBlank,
            referer: episode.link.trimmedNonBlank,
            publishedAt: published > 0 ? Date(timeIntervalSince1970: TimeInterval(published)) : nil,
            durationSeconds: episode.duration,
            enclosureURL: episode.enclosureUrl,
            enclosureType: episode.enclosureType
        )
    }
}
