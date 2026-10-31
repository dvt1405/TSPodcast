package tss.t.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/** M3-AC1: `progressOf` and the Player time labels. */
class PlaybackProgressTest {
    @Test
    fun progress() {
        assertEquals(0.5f, progressOf(5_000, 10_000))
        assertEquals(0f, progressOf(5_000, 0))
        assertEquals(0f, progressOf(5_000, -1))
        assertEquals(1f, progressOf(20_000, 10_000))
        assertEquals(0f, progressOf(-5, 10_000))
    }

    @Test
    fun timeLabels() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:00", formatPlaybackTime(-1))
        assertEquals("0:59", formatPlaybackTime(59_999))
        assertEquals("1:05", formatPlaybackTime(65_000))
        assertEquals("1:00", formatPlaybackTime(60_000))
        assertEquals("59:59", formatPlaybackTime(3_599_000))
        assertEquals("1:00:05", formatPlaybackTime(3_605_000))
        assertEquals("12:03:04", formatPlaybackTime(43_384_000))
    }
}
