import Foundation

/// What a tap hands to the player: the whole list the screen shows, in its order, and the
/// position of the tapped item (Android `playerEpisode(episode, podcast, listItems)` and
/// `playLive(liveEpisode, list)`, features-spec §3.4–§3.6).
struct PlaybackRequest: Equatable, Sendable {
    let items: [PlaybackItem]
    let startIndex: Int

    /// The tapped item; nil only when `startIndex` is out of range.
    var startItem: PlaybackItem? {
        items.indices.contains(startIndex) ? items[startIndex] : nil
    }
}

extension PlaybackRequest {
    /// Tap on Detail episode `startIndex`.
    init(podcast: PodcastRoute, episodes: [EpisodeRow], startIndex: Int) {
        self.init(items: episodes.map { PlaybackItem(episode: $0, podcast: podcast) }, startIndex: startIndex)
    }

    /// Tap on Live item `startIndex` of the flattened Live section.
    init(live: [LiveRowItem], startIndex: Int) {
        self.init(items: live.map(PlaybackItem.init(live:)), startIndex: startIndex)
    }
}
