package tss.t.shared.playback

/**
 * Port of `sharedPlayer/utils/progressOf`: the played fraction in `[0, 1]`, 0 when the duration
 * is unknown (`<= 0`, a live stream or `C.TIME_UNSET`).
 */
fun progressOf(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Time label of the Player like Media3 `Util.getStringForTime`: `M:SS` under an hour, `H:MM:SS`
 * from one hour. Negative values read as `0:00`.
 */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (if (ms < 0L) 0L else ms) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')
