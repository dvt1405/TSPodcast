package tss.t.shared.playback

import kotlinx.coroutines.test.runTest
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** M3-AC2: `extra:current_item_id` like Android `HistoryRepository`, items in memory. */
class PlaybackHistoryTest {
    private val store = InMemoryKeyValueStore()
    private val history = PlaybackHistory(store)

    @Test
    fun savesTheCurrentItemIdUnderTheAndroidKey() = runTest {
        assertNull(history.currentItemId())
        history.saveCurrent(testItem(7))
        assertEquals("7", store.getString("extra:current_item_id"))
        assertEquals("7", history.currentItemId())
        history.saveCurrent(testItem(8))
        assertEquals("8", history.currentItemId())
    }

    @Test
    fun recentItemsNewestFirstWithoutDuplicates() = runTest {
        history.saveCurrent(testItem(1))
        history.saveCurrent(testItem(2))
        history.saveCurrent(testItem(1))
        assertEquals(listOf("1", "2"), history.recentItems().map { it.id })
    }

    @Test
    fun recentItemsAreCapped() = runTest {
        (1..105).forEach { history.saveCurrent(testItem(it)) }
        val recent = history.recentItems()
        assertEquals(PlaybackHistory.MAX_RECENT_ITEMS, recent.size)
        assertEquals("105", recent.first().id)
        assertEquals("6", recent.last().id)
    }
}
