package tss.t.shared.blacklist

import kotlinx.coroutines.test.runTest
import tss.t.shared.network.FixedClock
import tss.t.shared.network.PodcastIndexJson
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M6-AC11: the local list of hidden shows. */
class HiddenShowsRepositoryTest {
    private val clock = FixedClock(1_000L)
    private val store = InMemoryKeyValueStore()

    private fun repository() = HiddenShowsRepository(store, PodcastIndexJson, clock)

    @Test
    fun startsEmpty() = runTest {
        val repository = repository()

        assertEquals(emptyList(), repository.all())
        assertEquals(emptySet(), repository.hiddenIds())
        assertFalse(repository.isHidden(1))
    }

    @Test
    fun hiddenShowSurvivesARelaunch() = runTest {
        repository().hide(feedId = 7, title = "Show 7", imageUrl = "https://img/7.jpg")

        val relaunched = repository()

        assertEquals(listOf(HiddenShow(7, "Show 7", "https://img/7.jpg", 1_000L)), relaunched.all())
        assertEquals(setOf(7L), relaunched.hiddenIds())
        assertTrue(relaunched.isHidden(7))
        assertTrue(store.snapshot().containsKey(HiddenShowsRepository.KEY_HIDDEN_SHOWS))
    }

    @Test
    fun unhideRemovesOnlyThatShow() = runTest {
        val repository = repository()
        repository.hide(1, "One", null)
        repository.hide(2, "Two", null)

        repository.unhide(1)
        repository.unhide(99)

        assertEquals(listOf(2L), repository().all().map { it.feedId })
    }

    @Test
    fun hidingTwiceKeepsOneRecordWithTheLatestTime() = runTest {
        val repository = repository()
        repository.hide(1, "Old title", null)
        clock.millis = 5_000L

        repository.hide(1, "New title", " ")

        assertEquals(listOf(HiddenShow(1, "New title", null, 5_000L)), repository.all())
    }

    @Test
    fun mostRecentlyHiddenComesFirst() = runTest {
        val repository = repository()
        repository.hide(1, "One", null)
        clock.millis = 2_000L
        repository.hide(2, "Two", null)

        assertEquals(listOf(2L, 1L), repository.all().map { it.feedId })
    }

    @Test
    fun brokenJsonReadsAsEmptyAndIsReplacedByTheNextWrite() = runTest {
        store.putString(HiddenShowsRepository.KEY_HIDDEN_SHOWS, "{not a list")
        val repository = repository()

        assertEquals(emptyList(), repository.all())
        repository.hide(3, "Three", null)
        assertEquals(setOf(3L), repository.hiddenIds())
    }
}
