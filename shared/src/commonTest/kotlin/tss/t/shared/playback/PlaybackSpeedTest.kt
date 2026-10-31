package tss.t.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** M6-AC6: speeds in tenths, 0.5–3.0, step 0.1. */
class PlaybackSpeedTest {
    @Test
    fun normalizeRoundsToATenth() {
        assertEquals(10, PlaybackSpeed.normalize(1.04f))
        assertEquals(11, PlaybackSpeed.normalize(1.06f))
        assertEquals(15, PlaybackSpeed.normalize(1.5f))
        assertEquals(8, PlaybackSpeed.normalize(0.8f))
        assertEquals(25, PlaybackSpeed.normalize(2.5f))
    }

    @Test
    fun normalizeClampsAndHandlesNaN() {
        assertEquals(5, PlaybackSpeed.normalize(0.1f))
        assertEquals(30, PlaybackSpeed.normalize(9f))
        assertEquals(10, PlaybackSpeed.normalize(Float.NaN))
        assertEquals(30, PlaybackSpeed.normalize(Float.POSITIVE_INFINITY))
        assertEquals(5, PlaybackSpeed.normalize(Float.NEGATIVE_INFINITY))
        assertEquals(5, PlaybackSpeed.normalize(-1f))
    }

    @Test
    fun chipsAndRemoteRates() {
        assertEquals(listOf(8, 10, 12, 15, 20), PlaybackSpeed.CHIPS)
        assertEquals(listOf(5, 8, 10, 12, 15, 20, 25, 30), PlaybackSpeed.REMOTE_RATES)
    }

    @Test
    fun labelAndRate() {
        assertEquals("1.5×", PlaybackSpeed.label(15))
        assertEquals("1.0×", PlaybackSpeed.label(10))
        assertEquals("0.5×", PlaybackSpeed.label(5))
        assertEquals("3.0×", PlaybackSpeed.label(30))
        assertEquals("3.0×", PlaybackSpeed.label(99), "clamped")
        assertEquals(1.5f, PlaybackSpeed.rate(15))
        assertEquals(1.0f, PlaybackSpeed.rate(PlaybackSpeed.DEFAULT_TENTHS))
    }

    @Test
    fun clamp() {
        assertEquals(5, PlaybackSpeed.clamp(0))
        assertEquals(30, PlaybackSpeed.clamp(31))
        assertEquals(12, PlaybackSpeed.clamp(12))
    }
}
