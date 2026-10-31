import Foundation
@preconcurrency import Shared

/// `SearchDataSource` backed by shared's `SearchFeed` (E7/E8 with the blacklist and the D6
/// `clean=true` + explicit filter, E1 by category with the 15-minute cache). This adapter only
/// maps Kotlin values to Swift structs and logs by type name. It never logs the query itself,
/// only its length (it may hold personal data).
///
/// `@unchecked Sendable`: an immutable reference to a Kotlin object, which Kotlin/Native lets
/// any thread call.
final class SharedSearchDataSource: SearchDataSource, @unchecked Sendable {
    private let feed: SearchFeed
    /// `-uitest-slow-network` (Debug only): wait this long before every shared call.
    private let delay: Duration?

    init(feed: SearchFeed, delay: Duration? = nil) {
        self.feed = feed
        self.delay = delay
    }

    func search(query: String, scope: SearchScope) async -> Result<[SearchResultItem], SearchLoadError> {
        await waitIfSlow()
        let mode = scope.sharedMode
        do {
            let state = try await feed.search(query: query, mode: mode)
            if let feeds = (state as? TSDataStateSuccess<NSArray>)?.data as? [Feed] {
                let items = feeds.map(SearchResultItem.init)
                AppLog.browse.notice(
                    "search_loaded mode=\(scope.rawValue, privacy: .public) length=\(query.count, privacy: .public) count=\(items.count, privacy: .public)"
                )
                return .success(items)
            }
            let reason = (state as? TSDataStateError<NSArray>)?.exception.typeName ?? "UnexpectedState"
            return .failure(Self.searchFailed(scope, reason: reason))
        } catch {
            // A Swift error out of a suspend call: only cancellation in practice.
            return .failure(Self.searchFailed(scope, reason: String(describing: type(of: error))))
        }
    }

    func categoryPodcasts(categoryId: Int) async -> Result<[PodcastCard], SearchLoadError> {
        await waitIfSlow()
        do {
            let state = try await feed.categoryPodcasts(categoryId: Int32(categoryId))
            if let podcasts = (state as? TSDataStateSuccess<NSArray>)?.data as? [Podcast] {
                let cards = podcasts.map(PodcastCard.init)
                AppLog.browse.notice(
                    "category_podcasts_loaded cat=\(categoryId, privacy: .public) count=\(cards.count, privacy: .public)"
                )
                return .success(cards)
            }
            let reason = (state as? TSDataStateError<NSArray>)?.exception.typeName ?? "UnexpectedState"
            return .failure(Self.categoryFailed(categoryId, reason: reason))
        } catch {
            return .failure(Self.categoryFailed(categoryId, reason: String(describing: type(of: error))))
        }
    }

    private func waitIfSlow() async {
        guard let delay else { return }
        try? await Task.sleep(for: delay)
    }

    private static func searchFailed(_ scope: SearchScope, reason: String) -> SearchLoadError {
        AppLog.browse.error("search_failed mode=\(scope.rawValue, privacy: .public) type=\(reason, privacy: .public)")
        return SearchLoadError(reason: reason)
    }

    private static func categoryFailed(_ categoryId: Int, reason: String) -> SearchLoadError {
        AppLog.browse.error("category_podcasts_failed cat=\(categoryId, privacy: .public) type=\(reason, privacy: .public)")
        return SearchLoadError(reason: reason)
    }
}

private extension SearchScope {
    var sharedMode: SearchMode {
        switch self {
        case .all: .all
        case .music: .music
        }
    }
}

extension SearchResultItem {
    /// Row of a Kotlin `Feed`. The Detail route goes through shared `Podcast.fromFeed` like
    /// Android's `HomeNavigationScreen` (cover = `feed.artwork`, else `feed.image`).
    init(_ feed: Feed) {
        let podcast = Podcast.companion.fromFeed(feed: feed)
        self.init(
            id: feed.id,
            title: feed.title,
            author: feed.author,
            categories: feed.categories ?? [:],
            image: feed.image,
            artwork: feed.artwork,
            summary: HtmlText.shared.toPlainText(html: feed.description_),
            route: PodcastRoute(PodcastCard(podcast))
        )
    }
}
