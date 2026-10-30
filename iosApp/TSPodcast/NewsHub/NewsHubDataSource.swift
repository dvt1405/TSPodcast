import Foundation
@preconcurrency import Shared

/// One episode of "Thời sự & Bản tin" (brief, "Mới nhất", night shelf) as views see it. Built
/// from shared's `NewsEpisode`; views never hold Kotlin objects.
struct NewsItem: Identifiable, Hashable, Sendable {
    /// Episode id.
    let id: Int64
    let feedId: Int64
    /// Config key of the source (`vov_thoisu`…), for logs only.
    let sourceKey: String
    let title: String
    /// The show's name as PodcastIndex has it (the publisher's own words, shown as is).
    let feedTitle: String
    /// Artwork of the publisher's feed (§3.4: "artwork của feed gốc"), not the episode image.
    let imageURL: String?
    /// `episode.link`, sent as `Referer` with the artwork request; nil when blank.
    let referer: String?
    /// `datePublished`; nil when 0.
    let publishedAt: Date?
    /// Seconds; 0 or less means unknown.
    let durationSeconds: Int64
    let enclosureURL: String
    /// `description` as plain text, stored with a favourite.
    var summary: String = ""
    /// The shared model, written to Room when the item plays (M4 §3.2); nil in unit-test fakes.
    var source: KotlinValue<NewsEpisode>? = nil
}

/// A tile of "Nguồn": the source's PodcastIndex feed, opened as a normal Podcast detail.
struct NewsSourceCard: Identifiable, Hashable, Sendable {
    /// Feed id.
    let id: Int64
    let key: String
    let title: String
    /// Description as plain text.
    let summary: String
    let imageURL: String?

    var route: PodcastRoute {
        PodcastRoute(feedId: id, title: title, summary: summary, imageURL: imageURL)
    }
}

/// Everything the Home section and the hub screen show (shared `NewsHub`, M6 §3.4).
struct NewsHubContent: Hashable, Sendable {
    /// "Điểm tin sáng" in play order; empty when no bulletin is in its time window.
    var brief: [NewsItem]
    /// "Mới nhất": at most 30, newest first.
    var latest: [NewsItem]
    /// "Nguồn" in config order.
    var sources: [NewsSourceCard]
    /// "Truyện đêm khuya": the night sources' episodes, source by source, newest first.
    var night: [NewsItem]
    /// Timer set when a night episode plays.
    var nightSleepTimerMinutes: Int

    /// Sum of the brief's durations (shared `MorningBrief.totalDurationSeconds`).
    var briefDurationSeconds: Int64 {
        brief.reduce(0) { $0 + max(0, $1.durationSeconds) }
    }

    /// Without a news episode the hub has nothing to lead with: no Home section, no screen.
    var hasNews: Bool { !latest.isEmpty }

    /// "Ẩn show này" (M6 §3.5): `feedId` leaves every list at once, without a request.
    func removing(feedId: Int64) -> NewsHubContent {
        NewsHubContent(
            brief: brief.filter { $0.feedId != feedId },
            latest: latest.filter { $0.feedId != feedId },
            sources: sources.filter { $0.id != feedId },
            night: night.filter { $0.feedId != feedId },
            nightSleepTimerMinutes: nightSleepTimerMinutes
        )
    }
}

/// How the hub was opened: `news_hub_open` parameter `from` (M6 §3.8).
enum NewsHubEntry: String, Hashable, Sendable {
    /// "Xem tất cả" of the Home section.
    case seeAll = "home_seeAll"
    /// The brief card of the Home section.
    case brief = "home_brief"
}

/// The news hub behind the Home section and the hub screen. `SharedNewsHubDataSource` is the
/// real one (shared `NewsHubRepository`); unit tests use a fake.
protocol NewsHubDataSource: Sendable {
    /// Config on and at least one news source left (shared `NewsHubConfigRepository.isEnabled`);
    /// no request. False: no Home section and no route.
    var isEnabled: Bool { get }

    /// The hub now; `.success(nil)` when it is off or nothing loaded (never an empty screen),
    /// `.failure` when no source loaded because of the network.
    func load() async -> Result<NewsHubContent?, HomeLoadError>
}
