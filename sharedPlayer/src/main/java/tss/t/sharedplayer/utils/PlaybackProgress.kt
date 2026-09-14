package tss.t.sharedplayer.utils

import androidx.media3.common.C

/**
 * Safe playback progress in 0f..1f.
 *
 * Before a media item is prepared, ExoPlayer reports `contentDuration` as
 * [C.TIME_UNSET] (`Long.MIN_VALUE + 1`), and `0` for a live stream with no
 * known duration. Dividing by either produces a negative or NaN float, and
 * Compose throws `IllegalArgumentException` when NaN reaches an `Animatable`
 * or a `Slider` value.
 */
fun progressOf(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L || durationMs == C.TIME_UNSET) return 0f
    val ratio = positionMs.toDouble() / durationMs.toDouble()
    if (ratio.isNaN()) return 0f
    return ratio.toFloat().coerceIn(0f, 1f)
}
