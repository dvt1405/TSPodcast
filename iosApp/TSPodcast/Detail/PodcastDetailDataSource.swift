import Foundation
@preconcurrency import Shared

/// Episodes of one podcast after the D6 filter (shared `PodcastDetail`, as Swift values).
struct PodcastDetailContent: Equatable, Sendable {
    /// What the screen lists, in API order.
    var episodes: [EpisodeRow]
    /// E11 marked the feed explicit and D6 is on: `episodes` is empty on purpose.
    var isExplicitFeedHidden = false
    /// Episodes removed by D6 (logs only).
    var hiddenExplicitCount = 0
    /// E10 failed and `episodes` are the ones saved on the device (M4 §3.3 [Sửa]): offline banner.
    var isFromCache = false
    /// The shared result behind `episodes` (same order), written to Room when one plays
    /// (M4 §3.2); nil in unit-test fakes and Debug hooks.
    var source: KotlinValue<PodcastDetail>? = nil
    /// `feed.episodeCount` of E11, nil when unknown: "Tìm trong show" also shows for a show with
    /// more than 20 episodes when Detail lists fewer (M6 §3.3).
    var feedEpisodeCount: Int? = nil
}

/// The episodes "Tìm trong show" searches (M6 §3.3, shared `ShowEpisodeIndex`).
protocol ShowEpisodeSearchIndex: Sendable {
    /// Episodes the search covers (E10 count before D6): the number in the footnote.
    var loadedCount: Int { get }
    /// The show has more episodes than `loadedCount`: "Chỉ tìm trong %d tập mới nhất".
    var isTruncated: Bool { get }
    /// Every episode containing every word of `query`, without accents, newest first.
    func matches(_ query: String) -> ShowSearchMatches
}

/// Search results as rows, plus the Kotlin episodes behind them for the Room history of a tap.
struct ShowSearchMatches: Equatable, Sendable {
    var episodes: [EpisodeRow]
    var sources: [KotlinValue<Episode>] = []

    /// The history of a tap on a result: the playlist is the results, saved with the podcast of
    /// the Detail list. Nil without Kotlin models (unit-test fakes): only the current id is saved.
    func history(basedOn detail: PlaybackHistorySource?) -> PlaybackHistorySource? {
        guard case .playlist(let podcast, _) = detail, sources.count == episodes.count, !sources.isEmpty else {
            return nil
        }
        return .playlist(podcast: podcast, episodes: sources.map(\.value))
    }
}

/// What the first search query of a Detail screen gets: the 1 000-episode index, or, when that
/// request failed, an index over the episodes already on screen (`isFallback`).
struct ShowSearchIndexResult: Sendable {
    let index: any ShowEpisodeSearchIndex
    let isFallback: Bool
}

/// Why the episodes could not load. Only the error type name is kept, for logs: the message
/// may hold a URL.
struct DetailLoadError: Error, Equatable, Sendable {
    let reason: String
}

/// Loader behind Podcast detail. `SharedPodcastDetailDataSource` is the real one (shared
/// `PodcastDetailFeed`: E10 ‖ E11, retry, 15-minute cache, D6); unit tests use a fake.
protocol PodcastDetailDataSource: Sendable {
    func load(feedId: Int64) async -> Result<PodcastDetailContent, DetailLoadError>
    /// "Tìm trong show": up to 1 000 episodes of the show (shared `ShowEpisodeSearch`, own cache
    /// entry), else an index over `onScreen`.
    func searchIndex(feedId: Int64, onScreen: PodcastDetailContent) async -> ShowSearchIndexResult
}
