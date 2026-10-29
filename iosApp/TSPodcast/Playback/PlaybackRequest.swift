import Foundation

/// What a tap on an episode hands to the player: the podcast, the whole list the screen shows
/// (in its order) and the position of the tapped episode, like Android's
/// `PlayerViewModel.playerEpisode(episode, podcast, listItems)` (features-spec §3.5).
struct PlaybackRequest: Equatable, Sendable {
    let podcast: PodcastRoute
    let episodes: [EpisodeRow]
    let startIndex: Int

    /// The tapped episode; nil only when `startIndex` is out of range.
    var startEpisode: EpisodeRow? {
        episodes.indices.contains(startIndex) ? episodes[startIndex] : nil
    }
}
