import Foundation
@preconcurrency import Shared

/// One item of the player queue as the Swift side sees it (views never hold Kotlin objects).
/// Same fields as shared `PlayableItem`, Android's `Episode.toMediaItem(album)`.
struct PlaybackItem: Identifiable, Hashable, Sendable {
    /// Episode id as a string (Android `mediaId`).
    let id: String
    let feedId: Int64
    /// Enclosure URL of the audio.
    let url: String
    let title: String
    /// Podcast name (`albumTitle`): Player subtitle, MiniPlayer subtitle, Now Playing artist.
    let podcastTitle: String
    let artworkURL: String?
    /// Sent as `Referer` with the artwork request.
    let referer: String?
    /// Duration announced by the feed, 0 when unknown; the player's own duration wins once known.
    let durationMs: Int64
    /// Declared live. Live section items are not: they count as live only when the stream has
    /// no duration (M3 §3.4), which the engine finds out once the item loads.
    let isLive: Bool
    /// Episode description as plain text (Android `MediaMetadata.description`), stored with a
    /// favourite; empty when unknown.
    var summary: String = ""
}

extension PlaybackItem {
    /// Detail tap (Android `playerEpisode(episode, podcast, listItems)`).
    init(episode: EpisodeRow, podcast: PodcastRoute) {
        self.init(
            id: String(episode.id),
            feedId: podcast.feedId,
            url: episode.enclosureURL,
            title: episode.title,
            podcastTitle: podcast.title,
            artworkURL: episode.imageURL ?? podcast.imageURL,
            referer: episode.referer,
            durationMs: max(0, episode.durationSeconds) * 1000,
            isLive: false,
            summary: episode.summary
        )
    }

    /// Home Live tap (Android `playLive`). The podcast name is `feedTitle`, else the title.
    init(live: LiveRowItem) {
        self.init(
            id: String(live.id),
            feedId: live.feedId,
            url: live.enclosureURL,
            title: live.title,
            podcastTitle: live.feedTitle ?? live.title,
            artworkURL: live.imageURL,
            referer: live.referer,
            durationMs: max(0, live.durationSeconds) * 1000,
            isLive: false,
            summary: live.summary
        )
    }

    /// News hub tap (M6 §3.4): the show's name and the publisher's feed artwork.
    init(news item: NewsItem) {
        self.init(
            id: String(item.id),
            feedId: item.feedId,
            url: item.enclosureURL,
            title: item.title,
            podcastTitle: item.feedTitle,
            artworkURL: item.imageURL,
            referer: item.referer,
            durationMs: max(0, item.durationSeconds) * 1000,
            isLive: false,
            summary: item.summary
        )
    }

    init(_ item: PlayableItem) {
        self.init(
            id: item.id,
            feedId: item.feedId,
            url: item.url,
            title: item.title,
            podcastTitle: item.podcastTitle,
            artworkURL: item.artworkUrl,
            referer: item.referer,
            durationMs: item.durationMs,
            isLive: item.isLive
        )
    }

    /// The shared item; `isLive` is the engine's effective value (declared or detected).
    func shared(isLive: Bool? = nil) -> PlayableItem {
        PlayableItem(
            id: id,
            feedId: feedId,
            url: url,
            title: title,
            podcastTitle: podcastTitle,
            artworkUrl: artworkURL,
            referer: referer,
            durationMs: durationMs,
            isLive: isLive ?? self.isLive
        )
    }

    /// Android `MediaItem.toFavouriteDTO()` of an episode: title, artwork and the episode
    /// description (the podcast name when the description is unknown, so the row is not blank).
    var favourite: FavouriteItem {
        FavouriteItem(
            id: id,
            type: .podcastepisode,
            title: title,
            image: artworkURL,
            description: summary.isEmpty ? podcastTitle : summary
        )
    }
}
