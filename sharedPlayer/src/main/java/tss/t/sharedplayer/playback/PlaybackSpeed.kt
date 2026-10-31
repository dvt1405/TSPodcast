package tss.t.sharedplayer.playback

import kotlin.math.roundToInt

/**
 * Playback speed in **tenths** (`15` = 1.5×), so stored and compared values never drift like
 * floats do. Same rules as iOS (`shared/.../playback/PlaybackSpeed.kt`, M6 N4):
 * range [MIN_TENTHS]..[MAX_TENTHS], step 1, quick chips [CHIPS].
 */
object PlaybackSpeed {
    const val MIN_TENTHS = 5
    const val MAX_TENTHS = 30
    const val DEFAULT_TENTHS = 10

    /** The quick choices of the speed sheet. */
    val CHIPS: List<Int> = listOf(8, 10, 12, 15, 20)

    /** A rate from anywhere (system controls, slider) rounded to 0.1 and clamped; NaN → 1.0×. */
    fun normalize(rate: Float): Int {
        if (rate.isNaN()) return DEFAULT_TENTHS
        return clamp((rate * 10f).roundToInt())
    }

    fun clamp(tenths: Int): Int = tenths.coerceIn(MIN_TENTHS, MAX_TENTHS)

    /** The player rate of [tenths]. */
    fun rate(tenths: Int): Float = clamp(tenths) / 10f

    /** `15` → `"1.5×"`. */
    fun label(tenths: Int): String {
        val value = clamp(tenths)
        return "${value / 10}.${value % 10}×"
    }
}
