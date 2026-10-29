import Foundation

/// The two mode chips of the Search tab (Android `SearchMode`, shared `SearchMode`). Named
/// `SearchScope` so it does not clash with the Kotlin enum. The raw value is the accessibility id
/// part: `search.mode.<rawValue>`.
enum SearchScope: String, CaseIterable, Hashable, Sendable {
    /// "Tất cả": E7 `search/byterm`.
    case all
    /// "Nhạc": E8 `search/music/byterm`.
    case music
}

/// A search result row as the UI sees it (Android `SearchPodcastItem` over a `Feed`); views never
/// hold Kotlin objects.
struct SearchResultItem: Identifiable, Hashable, Sendable {
    let id: Int64
    let title: String
    /// "`author` · up to 3 categories", parts joined by " · ", blank parts left out.
    let subtitle: String
    /// `description` as plain text (`HtmlText.toPlainText`, Android `HtmlCompat.fromHtml`).
    let summary: String
    /// `feed.image`, else `feed.artwork`; nil when both are blank.
    let imageURL: String?
    /// Where a tap goes (Android `Podcast.fromFeed(feed)` → PodcastDetail).
    let route: PodcastRoute

    /// Android shows at most 3 categories after the author.
    static let maxCategories = 3

    init(id: Int64, title: String, subtitle: String, summary: String, imageURL: String?, route: PodcastRoute) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.summary = summary
        self.imageURL = imageURL
        self.route = route
    }

    /// Row of a feed. `categories` is the PodcastIndex map id → name; the names follow the
    /// numeric order of the ids, which is the order the API sends and Android's
    /// `categories.values` keeps. Android leaves a trailing " · " when a feed has fewer than 3
    /// categories; this does not.
    init(
        id: Int64,
        title: String,
        author: String,
        categories: [String: String],
        image: String,
        artwork: String,
        summary: String,
        route: PodcastRoute
    ) {
        self.init(
            id: id,
            title: title,
            subtitle: Self.subtitle(author: author, categories: categories),
            summary: summary,
            imageURL: image.trimmedNonBlank ?? artwork.trimmedNonBlank,
            route: route
        )
    }

    static func subtitle(author: String, categories: [String: String]) -> String {
        let names = categories
            .sorted { lhs, rhs in
                switch (Int(lhs.key), Int(rhs.key)) {
                case let (left?, right?): left < right
                case (.some, nil): true
                case (nil, .some): false
                case (nil, nil): lhs.key < rhs.key
                }
            }
            .map(\.value)
            .compactMap(\.trimmedNonBlank)
            .prefix(maxCategories)
        return ([author.trimmedNonBlank].compactMap { $0 } + names).joined(separator: " · ")
    }
}

/// Why a search or a category list could not load. Only the error type name is kept, for logs:
/// the message may hold a URL.
struct SearchLoadError: Error, Equatable, Sendable {
    let reason: String
}

/// Loaders behind the Search tab. `SharedSearchDataSource` is the real one (shared `SearchFeed`:
/// E7/E8, blacklist, D6, and E1 by category); unit tests use a fake. Every call returns a value:
/// failures are `.failure`.
protocol SearchDataSource: Sendable {
    /// One search request for an already trimmed, non-blank `query`.
    func search(query: String, scope: SearchScope) async -> Result<[SearchResultItem], SearchLoadError>
    /// Podcasts trending in category `categoryId` (E1 `cat=<id>`, 7 days, `lang=vi`).
    func categoryPodcasts(categoryId: Int) async -> Result<[PodcastCard], SearchLoadError>
}

extension String {
    /// The string trimmed of whitespace and newlines, or nil when nothing is left.
    var trimmedNonBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
