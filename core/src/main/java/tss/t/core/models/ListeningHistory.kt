package tss.t.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One episode in the local listening history ("Lịch sử"), also the source of "Nghe tiếp".
 *
 * Self-contained on purpose: the cached `Episode` rows expire (PodcastIndex cache terms), while
 * this row is the user's own data and must still be able to show and replay the episode. It
 * never leaves the device; "Xoá lịch sử" and clearing the app data remove it.
 *
 * @property mediaId the player's media id (the episode id as text).
 * @property feedId the show, for its episode list and speed profile; null when unknown.
 * @property mediaUrl the audio URL, to replay when the cached episode row is gone.
 * @property positionMs last known position.
 * @property durationMs length reported by the player, 0 while unknown.
 * @property finished heard to (almost) the end, see [tss.t.core.history.ListeningHistoryRules].
 * @property lastPlayedAt wall-clock millis of the last update, the sort and grouping key.
 */
@Entity(
    tableName = "ListeningHistory",
    indices = [Index(value = ["lastPlayedAt"])]
)
data class ListeningHistory(
    @PrimaryKey
    val mediaId: String,
    val feedId: Long?,
    val title: String,
    val podcastTitle: String?,
    val image: String?,
    val mediaUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val lastPlayedAt: Long,
)
