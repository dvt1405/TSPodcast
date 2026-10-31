package tss.t.shared.playback

/**
 * One thing the player can play (architecture §4.1), the shared stand-in for Android's
 * `MediaItem` built by `Episode.toMediaItem`. iOS v1 has no radio, so there is no radio field.
 *
 * @property id episode id as a string (Android `mediaId`), the key of positions and history.
 * @property feedId feed of the episode (Detail marks the playing row of its own feed only).
 * @property url enclosure URL of the audio.
 * @property podcastTitle podcast name (`albumTitle`), shown under the title and on Now Playing.
 * @property referer `episode.link`, sent with the artwork request; null when blank.
 * @property durationMs duration announced by the feed, 0 when unknown.
 * @property isLive a live stream: no seek, no saved position.
 */
data class PlayableItem(
    val id: String,
    val feedId: Long,
    val url: String,
    val title: String,
    val podcastTitle: String,
    val artworkUrl: String?,
    val referer: String?,
    val durationMs: Long,
    val isLive: Boolean,
)
