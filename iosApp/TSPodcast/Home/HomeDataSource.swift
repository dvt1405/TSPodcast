import Foundation
@preconcurrency import Shared

/// A podcast card of Home (trending, music, recent and "Có thể bạn yêu thích"), as the UI sees it.
/// Built from shared's `Podcast`; views never hold Kotlin objects.
struct PodcastCard: Identifiable, Hashable, Sendable {
    let id: Int64
    let title: String
    /// `description` as plain text (`HtmlText.toPlainText`, Android `HtmlCompat.fromHtml`).
    let summary: String
    /// `podcast.image` (cover art), else `feedImage`, else `artwork`; `nil` when all are blank.
    let imageURL: String?
}

/// A row of the "Live Podcasts" pager (Android `LiveEpisodeWidgets`).
struct LiveRowItem: Identifiable, Hashable, Sendable {
    let id: Int64
    let title: String
    /// `datePublishedPretty`, already formatted by PodcastIndex.
    let subtitle: String
    /// `feedImage`, `nil` when blank.
    let imageURL: String?
    /// What the player needs (Android `Episode.fromLive`): feed, podcast name (`feedTitle`, nil
    /// when blank), audio URL, `link` (artwork `Referer`, nil when blank) and duration in seconds.
    var feedId: Int64 = 0
    var feedTitle: String? = nil
    var enclosureURL: String = ""
    var referer: String? = nil
    var durationSeconds: Int64 = 0
    /// `description` as plain text, stored with a favourite.
    var summary: String = ""
    /// The E2 model, written to Room when the row plays (M4 §3.2); nil in unit-test fakes.
    var source: KotlinValue<LiveEpisode>? = nil
}

/// Why a section could not load. Only the error type name is kept, for logs.
struct HomeLoadError: Error, Equatable, Sendable {
    let reason: String
}

/// "Podcasts thịnh hành" and "Có thể bạn yêu thích" come from one call, because
/// `MainViewModel`'s fallback needs both results (shared `HomeFeed.trendingSections`).
struct TrendingSectionsResult: Equatable, Sendable {
    var trending: Result<[PodcastCard], HomeLoadError>
    var forYou: Result<[PodcastCard], HomeLoadError>
}

/// The four loaders behind the five Home sections. `HomeFeedDataSource` is the real one (shared
/// `HomeFeed`); unit tests use a fake. Every call returns a value: failures are `.failure`.
protocol HomeDataSource: Sendable {
    /// Trending (E1, 7 days) and forYou (E1, 10 days, favourite categories), with the fallback.
    func trendingSections() async -> TrendingSectionsResult
    /// "Kho nhạc" (E6).
    func music() async -> Result<[PodcastCard], HomeLoadError>
    /// "Live Podcasts" (E2), not yet paged.
    func live() async -> Result<[LiveRowItem], HomeLoadError>
    /// "Podcasts xu hướng gần đây" (E5, favourite categories).
    func recent() async -> Result<[PodcastCard], HomeLoadError>
}
