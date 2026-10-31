package tss.t.shared.playback

import kotlin.math.roundToInt

/**
 * Playback speed in **tenths** (`15` = 1.5×), so stored and compared values never drift like
 * floats do (M6 §3.2, N4). Range [MIN_TENTHS]..[MAX_TENTHS], step 1.
 */
object PlaybackSpeed {
    const val MIN_TENTHS = 5
    const val MAX_TENTHS = 30
    const val DEFAULT_TENTHS = 10

    /** The quick choices of the speed sheet. */
    val CHIPS: List<Int> = listOf(8, 10, 12, 15, 20)

    /** The rates the Lock Screen offers (`changePlaybackRateCommand.supportedPlaybackRates`), in tenths. */
    val REMOTE_RATES: List<Int> = listOf(5, 8, 10, 12, 15, 20, 25, 30)

    /** A rate from anywhere (remote command, slider) rounded to 0.1 and clamped; NaN → 1.0×. */
    fun normalize(rate: Float): Int {
        if (rate.isNaN()) return DEFAULT_TENTHS
        return clamp((rate * 10f).roundToInt())
    }

    fun clamp(tenths: Int): Int = tenths.coerceIn(MIN_TENTHS, MAX_TENTHS)

    /** The player rate of [tenths]. */
    fun rate(tenths: Int): Float = tenths / 10f

    /** `15` → `"1.5×"`. */
    fun label(tenths: Int): String {
        val value = clamp(tenths)
        return "${value / 10}.${value % 10}×"
    }
}
