package tss.t.shared.playback

import tss.t.shared.preferences.KeyValueStore

/**
 * Listening position per episode ([Sửa] features-spec §3.6: Android keeps only the media id,
 * so every episode restarts from 0). The player calls [record] every [SAVE_INTERVAL_MS] while
 * playing, on pause, on episode change and when the app goes to the background (M3 §4.3), and
 * [resumePositionMs] before it starts an episode.
 *
 * Rules of [record]: live streams are never saved; the first [MIN_SAVED_POSITION_MS] are not
 * saved; with less than [FINISHED_REMAINING_MS] left the episode counts as finished and starts
 * from 0 next time. In both latter cases the saved value is removed.
 */
class PlaybackPositions(private val store: KeyValueStore) {

    suspend fun resumePositionMs(item: PlayableItem): Long {
        if (item.isLive) return 0L
        return store.getLong(key(item.id))?.takeIf { it > 0L } ?: 0L
    }

    suspend fun record(item: PlayableItem, positionMs: Long, durationMs: Long) {
        if (item.isLive) return
        val finished = durationMs > 0L && durationMs - positionMs < FINISHED_REMAINING_MS
        if (positionMs < MIN_SAVED_POSITION_MS || finished) {
            store.remove(key(item.id))
        } else {
            store.putLong(key(item.id), positionMs)
        }
    }

    companion object {
        const val KEY_PREFIX = "playback_position_"
        const val SAVE_INTERVAL_MS = 10_000L
        const val MIN_SAVED_POSITION_MS = 5_000L
        const val FINISHED_REMAINING_MS = 30_000L

        fun key(itemId: String): String = KEY_PREFIX + itemId
    }
}
