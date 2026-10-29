import Foundation

/// A podcast card of Home (trending, music, recent and "Có thể bạn yêu thích"), as the UI sees it.
/// Built from shared's `Podcast`; views never hold Kotlin objects.
struct PodcastCard: Identifiable, Hashable, Sendable {
    let id: Int64
    let title: String
    /// `description` as plain text (`HtmlText.toPlainText`, Android `HtmlCompat.fromHtml`).
    let summary: String
    /// `podcast.image` (cover art), `nil` when blank.
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
