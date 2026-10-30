import Foundation
@preconcurrency import Shared

/// A row of the Favourite tab (Android `FavouriteItemWidget` of a `FavouriteDTO`), as the UI sees it.
struct FavouriteRow: Identifiable, Hashable, Sendable {
    /// iOS v1 favourites: podcasts and episodes only (D1).
    enum Kind: Hashable, Sendable {
        case podcast
        case episode
    }

    /// `FavouriteDTO.id`: the episode or podcast id as a string.
    let itemID: String
    let kind: Kind
    let title: String
    /// `description` as plain text (Android `HtmlCompat.fromHtml`).
    let summary: String
    let imageURL: String?

    /// `(type, id)` is the table's primary key.
    var id: String { "\(kind)-\(itemID)" }

    /// VoiceOver label of the row: "title, description" (M4 §4.6).
    var accessibilityLabel: String {
        summary.isEmpty ? title : "\(title), \(summary)"
    }
}

/// What a resolved favourite tap does: play `request` and push Detail of `route`.
struct FavouritePlayback: Equatable, Sendable {
    let request: PlaybackRequest
    let route: PodcastRoute
}

/// Stops an observation when cancelled or released.
final class FavouriteObservation: @unchecked Sendable {
    private let lock = NSLock()
    private var onCancel: (() -> Void)?

    init(onCancel: @escaping () -> Void) {
        self.onCancel = onCancel
    }

    func cancel() {
        let action = lock.withLock {
            defer { onCancel = nil }
            return onCancel
        }
        action?()
    }

    deinit {
        cancel()
    }
}

/// The Favourite tab's data. `SharedFavouriteDataSource` is the real one (shared
/// `FavouriteRepository` + `FavouriteResolver`); unit tests use a fake that emits by hand.
@MainActor
protocol FavouriteDataSource: AnyObject {
    /// Calls `onChange` with the current list (newest first), then again after every change,
    /// until the returned observation is cancelled or released.
    func observe(_ onChange: @escaping @MainActor ([FavouriteRow]) -> Void) -> FavouriteObservation
    /// The saved podcast and episodes of `row`, or nil when the database cannot open it.
    func resolve(_ row: FavouriteRow) async -> FavouritePlayback?
    /// Deletes `row` from the table (Android `DeleteFavouriteUseCase`).
    func remove(_ row: FavouriteRow) async
}

/// `FavouriteDataSource` over the `FavouriteDTO` table and the saved Podcast/Episode rows.
@MainActor
final class SharedFavouriteDataSource: FavouriteDataSource {
    private let repository: FavouriteRepository
    private let resolver: FavouriteResolver

    init(repository: FavouriteRepository, resolver: FavouriteResolver) {
        self.repository = repository
        self.resolver = resolver
    }

    func observe(_ onChange: @escaping @MainActor ([FavouriteRow]) -> Void) -> FavouriteObservation {
        // `watch` delivers on the main queue.
        let handle = Watch_iosKt.watch(flow: repository.items) { value in
            let items = (value as? [FavouriteItem]) ?? []
            let rows = items.map(FavouriteRow.init)
            MainActor.assumeIsolated { onChange(rows) }
        }
        return FavouriteObservation { handle.close() }
    }

    func resolve(_ row: FavouriteRow) async -> FavouritePlayback? {
        do {
            guard let resolved = try await resolver.resolve(type: row.kind.sharedType, id: row.itemID) else {
                AppLog.browse.notice("favourite_unresolved type=\(row.kind.sharedType.name, privacy: .public) id=\(row.itemID, privacy: .public)")
                return nil
            }
            return FavouritePlayback(resolved)
        } catch {
            AppLog.browse.error("favourite_resolve_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return nil
        }
    }

    func remove(_ row: FavouriteRow) async {
        do {
            try await repository.setFavourite(item: row.sharedItem, isFavourite: false)
        } catch {
            AppLog.browse.error("favourite_remove_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }
}

extension FavouriteRow {
    init(_ item: FavouriteItem) {
        self.init(
            itemID: item.id,
            kind: item.type == .podcast ? .podcast : .episode,
            title: item.title,
            summary: HtmlText.shared.toPlainText(html: item.description_),
            imageURL: item.image.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
        )
    }

    var sharedItem: FavouriteItem {
        FavouriteItem(id: itemID, type: kind.sharedType, title: title, image: imageURL, description: summary)
    }
}

extension FavouriteRow.Kind {
    var sharedType: FavouriteType {
        switch self {
        case .podcast: .podcast
        case .episode: .podcastepisode
        }
    }
}

extension FavouritePlayback {
    /// Android `onFavouriteSelected`: the saved episodes (newest first) from the favourite one,
    /// saved again as the playlist (`playerEpisode` → `SaveCurrentPlayingUseCase`).
    init(_ resolved: ResolvedFavourite) {
        let route = PodcastRoute(PodcastCard(resolved.podcast))
        self.init(
            request: PlaybackRequest(
                podcast: route,
                episodes: resolved.episodes.map(EpisodeRow.init),
                startIndex: Int(resolved.startIndex),
                history: .playlist(podcast: resolved.podcast, episodes: resolved.episodes)
            ),
            route: route
        )
    }
}
