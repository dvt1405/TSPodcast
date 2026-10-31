package tss.t.sharedplayer.playback

/** The few key-value operations the playback stores need, so they can be tested without Android. */
interface PlaybackKeyValue {
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

/**
 * Listening position per episode (features-spec §3.6 [Sửa]: Android used to keep only the media
 * id, so every episode restarted from 0). Same rules as iOS `PlaybackPositions`: live streams
 * are never saved; the first [MIN_SAVED_POSITION_MS] are not saved; with less than
 * [FINISHED_REMAINING_MS] left the episode counts as finished and starts from 0 next time.
 */
class PlaybackPositions(private val store: PlaybackKeyValue) {

    fun resumePositionMs(mediaId: String, isLive: Boolean): Long {
        if (isLive) return 0L
        return store.getLong(key(mediaId))?.takeIf { it > 0L } ?: 0L
    }

    fun record(mediaId: String, isLive: Boolean, positionMs: Long, durationMs: Long) {
        if (isLive || mediaId.isBlank()) return
        val finished = durationMs > 0L && durationMs - positionMs < FINISHED_REMAINING_MS
        if (positionMs < MIN_SAVED_POSITION_MS || finished) {
            store.remove(key(mediaId))
        } else {
            store.putLong(key(mediaId), positionMs)
        }
    }

    companion object {
        const val KEY_PREFIX = "playback_position_"
        const val SAVE_INTERVAL_MS = 10_000L
        const val MIN_SAVED_POSITION_MS = 5_000L
        const val FINISHED_REMAINING_MS = 30_000L

        fun key(mediaId: String): String = KEY_PREFIX + mediaId
    }
}

/**
 * Remembered playback speed, like iOS `PlaybackProfileRepository`: one per show ([feedKey]), a
 * global one ([KEY_GLOBAL], default 1.0×) and the "apply to every show" switch
 * ([KEY_APPLY_TO_ALL], default off). With the switch on the global speed wins; with it off a
 * change is written for that show only. Items without a show (radio) use the global speed.
 */
class PlaybackProfiles(private val store: PlaybackKeyValue) {

    fun speedFor(feedId: Long?): Int {
        if (isApplyToAll() || feedId == null) return globalSpeed()
        return store.getLong(feedKey(feedId))?.let(::fromStored) ?: globalSpeed()
    }

    fun globalSpeed(): Int =
        store.getLong(KEY_GLOBAL)?.let(::fromStored) ?: PlaybackSpeed.DEFAULT_TENTHS

    fun isApplyToAll(): Boolean = store.getBoolean(KEY_APPLY_TO_ALL) ?: false

    /** Remembers [tenths] for [feedId], or for every show while "apply to all" is on (or without a show). */
    fun setSpeed(feedId: Long?, tenths: Int) {
        val value = PlaybackSpeed.clamp(tenths).toLong()
        if (isApplyToAll() || feedId == null) {
            store.putLong(KEY_GLOBAL, value)
        } else {
            store.putLong(feedKey(feedId), value)
        }
    }

    /** On: the global speed becomes [currentTenths] (per-show values are kept, ignored). Off: changes are per show again. */
    fun setApplyToAll(on: Boolean, currentTenths: Int) {
        if (on) store.putLong(KEY_GLOBAL, PlaybackSpeed.clamp(currentTenths).toLong())
        store.putBoolean(KEY_APPLY_TO_ALL, on)
    }

    private fun fromStored(value: Long): Int =
        value.coerceIn(PlaybackSpeed.MIN_TENTHS.toLong(), PlaybackSpeed.MAX_TENTHS.toLong()).toInt()

    companion object {
        const val KEY_GLOBAL = "playback.speed.global"
        const val KEY_APPLY_TO_ALL = "playback.speed.applyToAll"
        const val KEY_FEED_PREFIX = "playback.speed.feed."

        fun feedKey(feedId: Long): String = KEY_FEED_PREFIX + feedId
    }
}
