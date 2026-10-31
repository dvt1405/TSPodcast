import Foundation
@preconcurrency import Shared

/// A Kotlin model carried inside a Swift value type (a row, a request) without being read by
/// views. Kotlin data classes are immutable, so passing one between actors is safe; equality and
/// hashing are Kotlin's `equals`/`hashCode` (through `NSObject`).
struct KotlinValue<Value: NSObject>: Hashable, @unchecked Sendable {
    let value: Value

    init(_ value: Value) {
        self.value = value
    }
}

/// What a play writes to Room besides `extra:current_item_id` (M4 §3.2, Android
/// `SaveCurrentPlayingUseCase`): the API models behind the list, untouched, so the saved rows are
/// the full models and not something rebuilt from `PlaybackItem`.
enum PlaybackHistorySource: Hashable, @unchecked Sendable {
    /// Detail, Favourite and deep-link taps: `savePlaying(episode, podcast, playlist)`.
    case playlist(podcast: Podcast, episodes: [Episode])
    /// Home Live tap: `saveLive(live, playlist)` (Android `playLive`).
    case live([LiveEpisode])
    /// News hub (brief, "Mới nhất", night shelf, M6 §3.4): episodes of several shows, each saved
    /// with the podcast of its own feed, so "Tiếp tục nghe" and Favourite resolve every item.
    case news([NewsEpisode])

    /// Detail: the E10 episodes on screen and the podcast from E11, the saved row or the route.
    static func detail(_ detail: PodcastDetail, route: PodcastRoute) -> PlaybackHistorySource {
        .playlist(
            podcast: detail.podcastToSave(
                feedId: route.feedId,
                title: route.title,
                image: route.imageURL,
                description: route.summary
            ),
            episodes: detail.episodes
        )
    }
}

extension PlaybackHistory {
    /// Saves the play of `itemId`: the playlist and podcast of `source` when it holds that item,
    /// else (no source, or a unit-test request) only the current id, like previous/next.
    func record(_ source: PlaybackHistorySource?, itemId: String) async throws {
        switch source {
        case .playlist(let podcast, let episodes):
            if let episode = episodes.first(where: { String($0.id) == itemId }) {
                try await savePlaying(episode: episode, podcast: podcast, playlist: episodes)
                return
            }
        case .live(let items):
            if let live = items.first(where: { String($0.id) == itemId }) {
                try await saveLive(live: live, playlist: items)
                return
            }
        case .news(let items):
            if let start = items.firstIndex(where: { String($0.episode.id) == itemId }) {
                try await saveNews(items, startIndex: start)
                return
            }
        case nil:
            break
        }
        try await saveCurrentId(itemId: itemId)
    }

    /// One `savePlaying` per show (`NewsHistoryOrder`): the other shows first, the show of the
    /// started episode last, so `extra:current_item_id` ends on that episode.
    private func saveNews(_ items: [NewsEpisode], startIndex: Int) async throws {
        let groups = NewsHistoryOrder.groups(feedIds: items.map(\.feed.id), startIndex: startIndex)
        for group in groups {
            let episodes = group.indices.map { items[$0] }
            guard let first = episodes.first else { continue }
            let episode = group.indices.contains(startIndex) ? items[startIndex] : first
            try await savePlaying(
                episode: episode.episode,
                podcast: Podcast.companion.fromFeed(feed: first.feed),
                playlist: episodes.map(\.episode)
            )
        }
    }
}
