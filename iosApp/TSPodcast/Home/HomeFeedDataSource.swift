import Foundation
@preconcurrency import Shared

/// `HomeDataSource` backed by shared's `HomeFeed` (E1 trending/forYou with the fallback, E6, E2,
/// E5, blacklist and the D6 explicit filter all live in shared). This adapter only reads the
/// favourite category ids, maps Kotlin values to Swift structs and logs failures by type name.
///
/// `@unchecked Sendable`: it holds immutable references to Kotlin objects, which Kotlin/Native
/// lets any thread call.
final class HomeFeedDataSource: HomeDataSource, @unchecked Sendable {
    private let feed: HomeFeed
    private let favourites: FavouriteCategories
    /// `-uitest-slow-network` (Debug only): wait this long before every shared call.
    private let delay: Duration?

    /// Delay of `-uitest-slow-network`. M1 §4.7 says 3 s, but XCUITest waits for the animating
    /// shimmer to idle after every launch and drag (about 2 s each), so 3 s ran out before the
    /// test reached the lower sections; 10 s keeps them loading and still ends well inside 30 s.
    static let slowNetworkDelay: Duration = .seconds(10)

    init(feed: HomeFeed, favourites: FavouriteCategories, delay: Duration? = nil) {
        self.feed = feed
        self.favourites = favourites
        self.delay = delay
    }

    func trendingSections() async -> TrendingSectionsResult {
        await waitIfSlow()
        do {
            let catIds = try await favourites.favouriteCategoryIds()
            let sections = try await feed.trendingSections(favouriteCatIds: catIds)
            return TrendingSectionsResult(
                trending: Self.podcasts(sections.trending, section: "trending"),
                forYou: Self.podcasts(sections.forYou, section: "forYou")
            )
        } catch {
            let failure = Self.thrown(error, section: "trending")
            return TrendingSectionsResult(trending: .failure(failure), forYou: .failure(failure))
        }
    }

    func music() async -> Result<[PodcastCard], HomeLoadError> {
        await waitIfSlow()
        do {
            return Self.podcasts(try await feed.music(), section: "music")
        } catch {
            return .failure(Self.thrown(error, section: "music"))
        }
    }

    func live() async -> Result<[LiveRowItem], HomeLoadError> {
        await waitIfSlow()
        do {
            let state = try await feed.live()
            return Self.items(state, section: "live") { (episodes: [LiveEpisode]) in
                episodes.map(LiveRowItem.init)
            }
        } catch {
            return .failure(Self.thrown(error, section: "live"))
        }
    }

    func recent() async -> Result<[PodcastCard], HomeLoadError> {
        await waitIfSlow()
        do {
            let catIds = try await favourites.favouriteCategoryIds()
            return Self.podcasts(try await feed.recent(favouriteCatIds: catIds), section: "recent")
        } catch {
            return .failure(Self.thrown(error, section: "recent"))
        }
    }

    // MARK: - Mapping

    private func waitIfSlow() async {
        guard let delay else { return }
        try? await Task.sleep(for: delay)
    }

    private static func podcasts(_ state: TSDataState<NSArray>, section: String) -> Result<[PodcastCard], HomeLoadError> {
        items(state, section: section) { (podcasts: [Podcast]) in podcasts.map(PodcastCard.init) }
    }

    /// `Success` → mapped list; `Error` → failure with the Kotlin type name (never the message:
    /// it may hold a URL); `Loading` never comes out of a finished call and counts as a failure.
    private static func items<Kotlin, Item>(
        _ state: TSDataState<NSArray>,
        section: String,
        map: ([Kotlin]) -> [Item]
    ) -> Result<[Item], HomeLoadError> {
        if let success = state as? TSDataStateSuccess<NSArray>, let list = success.data as? [Kotlin] {
            let items = map(list)
            AppLog.app.notice("home_section_loaded section=\(section, privacy: .public) count=\(items.count, privacy: .public)")
            return .success(items)
        }
        let reason = (state as? TSDataStateError<NSArray>)?.exception.typeName ?? "UnexpectedState"
        AppLog.app.error("home_section_failed section=\(section, privacy: .public) type=\(reason, privacy: .public)")
        return .failure(HomeLoadError(reason: reason))
    }

    /// A Swift error out of a suspend call: only cancellation in practice.
    private static func thrown(_ error: any Error, section: String) -> HomeLoadError {
        let reason = String(describing: type(of: error))
        AppLog.app.error("home_section_failed section=\(section, privacy: .public) type=\(reason, privacy: .public)")
        return HomeLoadError(reason: reason)
    }
}

extension PodcastCard {
    init(_ podcast: Podcast) {
        self.init(
            id: podcast.id,
            title: podcast.title ?? "",
            summary: HtmlText.shared.toPlainText(html: podcast.description_),
            imageURL: podcast.image.nonBlank ?? podcast.feedImage?.nonBlank ?? podcast.artwork?.nonBlank
        )
    }
}

extension LiveRowItem {
    init(_ episode: LiveEpisode) {
        self.init(
            id: episode.id,
            title: episode.title,
            subtitle: episode.datePublishedPretty,
            imageURL: episode.feedImage.nonBlank
        )
    }
}

private extension String {
    var nonBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
