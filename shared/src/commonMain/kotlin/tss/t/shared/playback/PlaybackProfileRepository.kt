package tss.t.shared.playback

import tss.t.shared.preferences.KeyValueStore

/**
 * Remembered playback speed (M6 §3.2, N4) in the app's key-value store: one per show
 * ([feedKey]), a global one ([KEY_GLOBAL], default 1.0×) and the "apply to every show" switch
 * ([KEY_APPLY_TO_ALL], default off). With the switch on, the global speed wins and per-show
 * values stay stored but ignored; with it off, a change is written for that show only.
 * Stored values are clamped on read.
 */
class PlaybackProfileRepository(private val store: KeyValueStore) {

    suspend fun speedFor(feedId: Long): Int {
        if (isApplyToAll()) return globalSpeed()
        return store.getLong(feedKey(feedId))?.let(::fromStored) ?: globalSpeed()
    }

    suspend fun globalSpeed(): Int =
        store.getLong(KEY_GLOBAL)?.let(::fromStored) ?: PlaybackSpeed.DEFAULT_TENTHS

    suspend fun isApplyToAll(): Boolean = store.getBoolean(KEY_APPLY_TO_ALL) ?: false

    /** Remembers [tenths] for [feedId], or for every show while "apply to all" is on. */
    suspend fun setSpeed(feedId: Long, tenths: Int) {
        val value = PlaybackSpeed.clamp(tenths).toLong()
        if (isApplyToAll()) {
            store.putLong(KEY_GLOBAL, value)
        } else {
            store.putLong(feedKey(feedId), value)
        }
    }

    /**
     * On: the global speed becomes [currentTenths] (per-show values are kept, ignored). Off:
     * from now on changes are per show again.
     */
    suspend fun setApplyToAll(on: Boolean, currentTenths: Int) {
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
