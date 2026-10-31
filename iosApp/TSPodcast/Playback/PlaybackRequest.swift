import Foundation

/// What a tap hands to the player: the whole list the screen shows, in its order, and the
/// position of the tapped item (Android `playerEpisode(episode, podcast, listItems)` and
/// `playLive(liveEpisode, list)`, features-spec §3.4–§3.6).
struct PlaybackRequest: Equatable, Sendable {
    let items: [PlaybackItem]
    let startIndex: Int
    /// The API models written to Room when the request starts (M4 §3.2); nil only writes
    /// `extra:current_item_id`.
    var history: PlaybackHistorySource?
    /// Moves on to the next item at the end of one (M6 §3.1). False everywhere (Android
    /// `setPauseAtEndOfMediaItems(true)`) except "Play all" of the morning brief.
    var autoAdvance = false

    /// The tapped item; nil only when `startIndex` is out of range.
    var startItem: PlaybackItem? {
        items.indices.contains(startIndex) ? items[startIndex] : nil
    }
}

extension PlaybackRequest {
    /// Tap on Detail episode `startIndex` (also a Favourite tap, from the saved rows).
    init(podcast: PodcastRoute, episodes: [EpisodeRow], startIndex: Int, history: PlaybackHistorySource? = nil) {
        self.init(
            items: episodes.map { PlaybackItem(episode: $0, podcast: podcast) },
            startIndex: startIndex,
            history: history
        )
    }

    /// Tap on Live item `startIndex` of the flattened Live section; saved like Android `playLive`.
    init(live: [LiveRowItem], startIndex: Int) {
        let sources = live.compactMap { $0.source?.value }
        self.init(
            items: live.map(PlaybackItem.init(live:)),
            startIndex: startIndex,
            history: sources.isEmpty ? nil : .live(sources)
        )
    }

    /// A list of the news hub (M6 §3.4) from `startIndex`. Only "Phát tất cả" of the morning brief
    /// turns auto-advance on; "Mới nhất" and the night shelf play one episode at a time.
    init(news: [NewsItem], startIndex: Int, autoAdvance: Bool = false) {
        let sources = news.compactMap { $0.source?.value }
        self.init(
            items: news.map(PlaybackItem.init(news:)),
            startIndex: startIndex,
            history: sources.count == news.count && !sources.isEmpty ? .news(sources) : nil,
            autoAdvance: autoAdvance
        )
    }
}
