import Foundation
@preconcurrency import Shared
@testable import TSPodcast

/// Scripted `NewsHubDataSource`: a switchable `isEnabled`, one result for every load, a call
/// count, and loads that can wait until `release`.
final class FakeNewsHubDataSource: NewsHubDataSource, @unchecked Sendable {
    private let lock = NSLock()
    private var _isEnabled: Bool
    private var _result: Result<NewsHubContent?, HomeLoadError>
    private var _loads = 0
    private var isHolding = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    init(isEnabled: Bool = true, result: Result<NewsHubContent?, HomeLoadError> = .success(.sample)) {
        _isEnabled = isEnabled
        _result = result
    }

    var isEnabled: Bool { lock.withLock { _isEnabled } }
    var loads: Int { lock.withLock { _loads } }

    func setEnabled(_ value: Bool) { lock.withLock { _isEnabled = value } }
    func setResult(_ result: Result<NewsHubContent?, HomeLoadError>) { lock.withLock { _result = result } }

    /// Loads wait until `release` from now on.
    func hold() { lock.withLock { isHolding = true } }

    func release() {
        let continuations = lock.withLock {
            isHolding = false
            defer { waiting.removeAll() }
            return waiting
        }
        continuations.forEach { $0.resume() }
    }

    func load() async -> Result<NewsHubContent?, HomeLoadError> {
        let mustWait = lock.withLock {
            _loads += 1
            return isHolding
        }
        if mustWait {
            await withCheckedContinuation { continuation in
                let resumeNow = lock.withLock {
                    guard isHolding else { return true }
                    waiting.append(continuation)
                    return false
                }
                if resumeNow { continuation.resume() }
            }
        }
        return lock.withLock { _result }
    }
}

/// `NewsHubPlayback` that records every call.
@MainActor
final class RecordingNewsHubPlayback: NewsHubPlayback {
    struct Play: Equatable {
        let request: PlaybackRequest
        let presentsPlayer: Bool
    }

    private(set) var plays: [Play] = []
    private(set) var timers: [(setting: SleepTimerSetting, origin: String)] = []

    func play(_ request: PlaybackRequest, presentsPlayer: Bool) {
        plays.append(Play(request: request, presentsPlayer: presentsPlayer))
    }

    func setSleepTimer(_ setting: SleepTimerSetting, origin: String) {
        timers.append((setting, origin))
    }
}

extension NewsItem {
    /// Episode `id` of feed `feedId`, 5 minutes long.
    static func sample(_ id: Int64, feedId: Int64 = 7, duration: Int64 = 300) -> NewsItem {
        NewsItem(
            id: id,
            feedId: feedId,
            sourceKey: "source\(feedId)",
            title: "Bản tin \(id)",
            feedTitle: "Show \(feedId)",
            imageURL: "https://img.example.com/feed\(feedId).jpg",
            referer: nil,
            publishedAt: Date(timeIntervalSince1970: 1_761_782_400 + TimeInterval(id)),
            durationSeconds: duration,
            enclosureURL: "https://media.example.com/n\(id).mp3"
        )
    }
}

extension NewsHubContent {
    /// Brief of 5 items over 3 shows (4 × 300 s + 180 s = 23 min), 6 latest, 2 sources, 2 night
    /// episodes with a 30-minute timer.
    static let sample = NewsHubContent(
        brief: [.sample(1, feedId: 7), .sample(2, feedId: 8), .sample(3, feedId: 9), .sample(4, feedId: 7), .sample(5, feedId: 8, duration: 180)],
        latest: (11...16).map { NewsItem.sample($0, feedId: $0 % 2 == 0 ? 7 : 8) },
        sources: [
            NewsSourceCard(id: 7, key: "vov_thoisu", title: "Thời sự VOV", summary: "VOV", imageURL: "https://img.example.com/feed7.jpg"),
            NewsSourceCard(id: 8, key: "vnexpress_homnay", title: "VnExpress hôm nay", summary: "VnExpress", imageURL: nil),
        ],
        night: [.sample(21, feedId: 30), .sample(22, feedId: 30)],
        nightSleepTimerMinutes: 30
    )
}

extension SharedModelFactory {
    /// PodcastIndex feed `id` (Kotlin default arguments do not reach Swift).
    static func feed(_ id: Int64, title: String? = nil) -> Feed {
        Feed(
            artwork: "https://img.example.com/feed\(id).jpg",
            author: "Publisher \(id)",
            categories: nil,
            contentType: "application/rss+xml",
            crawlErrors: 0,
            dead: 0,
            description: "About feed \(id)",
            episodeCount: 20,
            explicit: false,
            generator: "",
            id: id,
            image: "",
            imageUrlHash: 0,
            itunesId: nil,
            language: "vi",
            lastCrawlTime: 0,
            lastGoodHttpStatusTime: 0,
            lastHttpStatus: 200,
            lastParseTime: 0,
            lastUpdateTime: 0,
            link: "https://news.example.com/\(id)",
            locked: 0,
            medium: "podcast",
            newestItemPubdate: 0,
            originalUrl: "https://news.example.com/\(id).rss",
            ownerName: "Publisher \(id)",
            parseErrors: 0,
            podcastGuid: "",
            title: title ?? "Feed \(id)",
            type: 0,
            url: "https://news.example.com/\(id).rss",
            chash: nil,
            funding: nil,
            itunesType: nil,
            value: nil
        )
    }

    static func newsEpisode(_ id: Int64, feed: Feed) -> NewsEpisode {
        NewsEpisode(sourceKey: "source\(feed.id)", feed: feed, episode: episode(id, feedId: feed.id))
    }
}
