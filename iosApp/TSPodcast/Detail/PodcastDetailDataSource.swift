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
}
