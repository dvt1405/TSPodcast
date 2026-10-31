package tss.t.shared.playback

import kotlinx.coroutines.test.runTest
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M3-AC2: listening positions ([Sửa] §3.6, M3 §4.3). */
class PlaybackPositionsTest {
    private val store = InMemoryKeyValueStore()
    private val positions = PlaybackPositions(store)
    private val item = testItem(1)

    @Test
    fun savesAndResumes() = runTest {
        positions.record(item, 120_000, 600_000)
        assertEquals(120_000, positions.resumePositionMs(item))
        assertEquals(120_000L, store.getLong("playback_position_1"))
    }

    @Test
    fun firstFiveSecondsAreNotSaved() = runTest {
        positions.record(item, 120_000, 600_000)
        positions.record(item, 4_000, 600_000)
        assertEquals(0, positions.resumePositionMs(item))
        assertFalse(store.snapshot().containsKey("playback_position_1"))
    }

    @Test
    fun underThirtySecondsLeftCountsAsFinished() = runTest {
        positions.record(item, 120_000, 600_000)
        positions.record(item, 575_000, 600_000)
        assertEquals(0, positions.resumePositionMs(item))
        assertTrue(store.snapshot().isEmpty())

        positions.record(item, 569_000, 600_000)
        assertEquals(569_000, positions.resumePositionMs(item))
    }

    @Test
    fun liveIsNeverSaved() = runTest {
        val live = testItem(2, isLive = true)
        positions.record(live, 120_000, 0)
        assertEquals(0, positions.resumePositionMs(live))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun unknownDurationStillSaves() = runTest {
        positions.record(item, 5_000, 0)
        assertEquals(5_000, positions.resumePositionMs(item))
    }

    @Test
    fun positionsArePerEpisode() = runTest {
        positions.record(item, 60_000, 600_000)
        assertEquals(0, positions.resumePositionMs(testItem(3)))
    }
}
