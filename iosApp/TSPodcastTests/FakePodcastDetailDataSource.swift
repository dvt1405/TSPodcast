import Foundation
@testable import TSPodcast

/// Scripted `PodcastDetailDataSource`: answers with queued results (the last one repeats),
/// records the feed ids it was asked for and can hold calls until `releaseAll`.
final class FakePodcastDetailDataSource: PodcastDetailDataSource, @unchecked Sendable {
    private let lock = NSLock()
    private var results: [Result<PodcastDetailContent, DetailLoadError>]
    private var _requests: [Int64] = []
    private var isHolding = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    init(_ results: Result<PodcastDetailContent, DetailLoadError>...) {
        self.results = results.isEmpty ? [.success(PodcastDetailContent(episodes: []))] : results
    }

    /// Feed ids of every call, in order.
    var requests: [Int64] { lock.withLock { _requests } }

    /// Calls from now on wait until `releaseAll`.
    func hold() { lock.withLock { isHolding = true } }

    func releaseAll() {
        let continuations = lock.withLock {
            isHolding = false
            defer { waiting.removeAll() }
            return waiting
        }
        continuations.forEach { $0.resume() }
    }

    var heldCount: Int { lock.withLock { waiting.count } }

    /// What `searchIndex` answers (M6 §3.3); nil: an index over the episodes on screen.
    var searchResult: ShowSearchIndexResult? {
        get { lock.withLock { _searchResult } }
        set { lock.withLock { _searchResult = newValue } }
    }
    private var _searchResult: ShowSearchIndexResult?
    private var _searchRequests: [Int64] = []

    /// Feed ids of every `searchIndex` call.
    var searchRequests: [Int64] { lock.withLock { _searchRequests } }

    func searchIndex(feedId: Int64, onScreen: PodcastDetailContent) async -> ShowSearchIndexResult {
        let result = lock.withLock {
            _searchRequests.append(feedId)
            return _searchResult
        }
        return result ?? ShowSearchIndexResult(index: FakeShowSearchIndex(rows: onScreen.episodes), isFallback: true)
    }

    func load(feedId: Int64) async -> Result<PodcastDetailContent, DetailLoadError> {
        let (mustWait, result) = lock.withLock {
            _requests.append(feedId)
            let result = results.count > 1 ? results.removeFirst() : results[0]
            return (isHolding, result)
        }
        if mustWait {
            await withCheckedContinuation { continuation in
                lock.withLock { waiting.append(continuation) }
            }
        }
        return result
    }
}

/// `ShowEpisodeSearchIndex` matching titles that contain every word, case-insensitively (no fold:
/// shared `EpisodeFilter` has its own tests).
struct FakeShowSearchIndex: ShowEpisodeSearchIndex {
    let rows: [EpisodeRow]
    var loadedCount: Int
    var isTruncated: Bool

    init(rows: [EpisodeRow], loadedCount: Int? = nil, isTruncated: Bool = false) {
        self.rows = rows
        self.loadedCount = loadedCount ?? rows.count
        self.isTruncated = isTruncated
    }

    func matches(_ query: String) -> ShowSearchMatches {
        let words = query.lowercased().split(separator: " ")
        return ShowSearchMatches(episodes: rows.filter { row in words.allSatisfy { row.title.lowercased().contains($0) } })
    }
}

/// `PlaybackController` that records every request; `state` is set by the test.
@MainActor
final class RecordingPlaybackController: PlaybackController {
    private(set) var requests: [PlaybackRequest] = []
    var state = PlaybackState()

    func play(_ request: PlaybackRequest) {
        requests.append(request)
    }
}

extension EpisodeRow {
    static func sample(_ id: Int64) -> EpisodeRow {
        EpisodeRow(
            id: id,
            title: "Episode \(id)",
            summary: "Summary \(id)",
            imageURL: "https://img.example.com/e\(id).jpg",
            referer: "https://site.example.com/e\(id)",
            publishedAt: Date(timeIntervalSince1970: 1_761_782_400),
            durationSeconds: 1800,
            enclosureURL: "https://media.example.com/e\(id).mp3",
            enclosureType: "audio/mpeg"
        )
    }
}

extension PodcastRoute {
    static let sample = PodcastRoute(feedId: 42, title: "Podcast 42", summary: "About 42", imageURL: nil)
}

extension DetailLoadError {
    static let network = DetailLoadError(reason: "ResponseException")
}
