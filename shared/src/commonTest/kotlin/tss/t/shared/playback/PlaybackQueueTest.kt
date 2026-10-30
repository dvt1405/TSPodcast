package tss.t.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M3-AC1: playlist + position, moves only when there is an item there. */
class PlaybackQueueTest {
    private val items = (0..2).map { testItem(it) }

    @Test
    fun emptyListHasNoQueue() {
        assertNull(PlaybackQueue.of(emptyList(), 0))
    }

    @Test
    fun startIndexIsClamped() {
        assertEquals(0, PlaybackQueue.of(items, -3)?.index)
        assertEquals(2, PlaybackQueue.of(items, 9)?.index)
        assertEquals(1, PlaybackQueue.of(items, 1)?.index)
        assertEquals(items[1], PlaybackQueue.of(items, 1)?.current)
    }

    @Test
    fun endsHaveNoPreviousOrNext() {
        val first = assertNotNull(PlaybackQueue.of(items, 0))
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)
        assertNull(first.previous())

        val last = assertNotNull(PlaybackQueue.of(items, 2))
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
        assertNull(last.next())
    }

    @Test
    fun movesReturnNewQueues() {
        val queue = assertNotNull(PlaybackQueue.of(items, 0))
        val next = assertNotNull(queue.next())
        assertEquals(1, next.index)
        assertEquals(0, queue.index)
        assertEquals(0, next.previous()?.index)
        assertEquals(3, next.size)
    }

    @Test
    fun selectOutOfRangeIsNull() {
        val queue = assertNotNull(PlaybackQueue.of(items, 1))
        assertNull(queue.select(-1))
        assertNull(queue.select(3))
        assertEquals(items[2], queue.select(2)?.current)
    }

    @Test
    fun singleItemQueue() {
        val queue = assertNotNull(PlaybackQueue.of(listOf(testItem(7)), 0))
        assertFalse(queue.hasPrevious)
        assertFalse(queue.hasNext)
    }
}
