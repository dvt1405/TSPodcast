import Foundation
@preconcurrency import Shared
@testable import TSPodcast

/// Kotlin API models for tests (Kotlin default arguments do not reach Swift).
enum SharedModelFactory {
    /// Episode `id` of feed `feedId`, with an audio URL and a publish date that grows with `id`.
    static func episode(_ id: Int64, feedId: Int64 = 42, published: Int64? = nil, title: String? = nil) -> Episode {
        Episode(
            chaptersUrl: nil,
            dateCrawled: 0,
            datePublished: published ?? 1_761_782_400 + id,
            datePublishedPretty: "",
            description: "<p>Summary \(id)</p>",
            duration: 1800,
            enclosureLength: 0,
            enclosureType: "audio/mpeg",
            enclosureUrl: "https://media.example.com/e\(id).mp3",
            episode: nil,
            episodeType: nil,
            explicit: 0,
            feedDead: 0,
            feedDuplicateOf: nil,
            feedId: feedId,
            feedImage: "",
            feedItunesId: nil,
            feedLanguage: nil,
            feedUrl: "",
            guid: "guid-\(id)",
            id: id,
            image: "https://img.example.com/e\(id).jpg",
            link: "https://site.example.com/e\(id)",
            persons: nil,
            podcastGuid: "",
            season: 0,
            socialInteract: nil,
            soundbite: nil,
            soundbites: nil,
            title: title ?? "Episode \(id)",
            transcriptUrl: nil,
            transcripts: nil,
            value: nil
        )
    }

    /// Podcast `feedId` (`id == feedId`), built the way Detail builds it without E11.
    static func podcast(_ feedId: Int64 = 42, title: String = "Podcast 42") -> Podcast {
        PodcastDetail(episodes: [], feed: nil, hiddenExplicitCount: 0, isExplicitFeedHidden: false, isFromCache: false, podcast: nil)
            .podcastToSave(feedId: feedId, title: title, image: nil, description: "About \(feedId)")
    }

    /// Live item `id` of feed `feedId`.
    static func live(_ id: Int64, feedId: Int64 = 900) -> LiveEpisode {
        LiveEpisode(
            categories: nil,
            chaptersUrl: nil,
            contentLink: "",
            dateCrawled: 0,
            datePublished: 1_761_782_400,
            datePublishedPretty: "Today",
            description: "Live description \(id)",
            duration: 0,
            enclosureLength: 0,
            enclosureType: "audio/mpeg",
            enclosureUrl: "https://media.example.com/live\(id).mp3",
            endTime: 0,
            episode: 0,
            episodeType: nil,
            explicit: 0,
            feedDead: 0,
            feedDuplicateOf: 0,
            feedId: feedId,
            feedImage: "https://img.example.com/feed\(feedId).jpg",
            feedItunesId: nil,
            feedLanguage: nil,
            feedTitle: "Feed \(feedId)",
            guid: "live-\(id)",
            id: id,
            image: "",
            link: "",
            season: 0,
            startTime: 0,
            status: "live",
            title: "Live \(id)",
            transcriptUrl: nil
        )
    }

    /// Detail request over `episodes` of `podcast`, with the Room history like the real screen.
    @MainActor
    static func detailRequest(podcast: Podcast, episodes: [Episode], startIndex: Int) -> PlaybackRequest {
        PlaybackRequest(
            podcast: PodcastRoute(PodcastCard(podcast)),
            episodes: episodes.map(EpisodeRow.init),
            startIndex: startIndex,
            history: .playlist(podcast: podcast, episodes: episodes)
        )
    }
}

/// `FavouriteDataSource` driven by the test: `emit` plays the role of the Room `Flow`.
@MainActor
final class FakeFavouriteDataSource: FavouriteDataSource {
    private var observers: [UUID: @MainActor ([FavouriteRow]) -> Void] = [:]
    private var current: [FavouriteRow]?
    /// What `resolve` answers per row id; a missing id is "cannot open".
    var resolutions: [String: FavouritePlayback] = [:]
    private(set) var resolved: [FavouriteRow] = []
    private(set) var removed: [FavouriteRow] = []
    private(set) var observeCount = 0

    var observerCount: Int { observers.count }

    func observe(_ onChange: @escaping @MainActor ([FavouriteRow]) -> Void) -> FavouriteObservation {
        observeCount += 1
        let id = UUID()
        observers[id] = onChange
        if let current { onChange(current) }
        return FavouriteObservation { [weak self] in
            MainActor.assumeIsolated { _ = self?.observers.removeValue(forKey: id) }
        }
    }

    func emit(_ rows: [FavouriteRow]) {
        current = rows
        observers.values.forEach { $0(rows) }
    }

    func resolve(_ row: FavouriteRow) async -> FavouritePlayback? {
        resolved.append(row)
        return resolutions[row.id]
    }

    func remove(_ row: FavouriteRow) async {
        removed.append(row)
        emit((current ?? []).filter { $0 != row })
    }
}

extension FavouriteRow {
    static func episode(_ id: Int64, title: String? = nil) -> FavouriteRow {
        FavouriteRow(itemID: String(id), kind: .episode, title: title ?? "Episode \(id)", summary: "Summary \(id)", imageURL: nil)
    }

    static func podcast(_ id: Int64) -> FavouriteRow {
        FavouriteRow(itemID: String(id), kind: .podcast, title: "Podcast \(id)", summary: "", imageURL: nil)
    }
}
