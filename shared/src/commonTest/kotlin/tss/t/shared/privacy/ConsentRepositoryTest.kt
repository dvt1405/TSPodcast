package tss.t.shared.privacy

import kotlinx.coroutines.test.runTest
import tss.t.shared.network.FixedClock
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M6-AC13: consent to analytics and crash reports, with version and time. */
class ConsentRepositoryTest {
    private val clock = FixedClock(1_761_800_000_000L)
    private val store = InMemoryKeyValueStore()

    private fun repository() = ConsentRepository(store, clock)

    @Test
    fun freshInstallNeedsThePromptAndAllowsNothing() = runTest {
        val state = repository().state()

        assertTrue(state.needsPrompt)
        assertTrue(repository().needsPrompt())
        assertFalse(state.analytics)
        assertFalse(state.crash)
        assertEquals(0, state.version)
        assertNull(state.decidedAtMillis)
    }

    @Test
    fun acceptAllStoresVersionAndTime() = runTest {
        val state = repository().acceptAll()

        assertEquals(ConsentState(version = 1, decidedAtMillis = 1_761_800_000_000L, analytics = true, crash = true, needsPrompt = false), state)
        assertEquals(state, repository().state(), "a relaunch reads the same choice")
        assertFalse(repository().needsPrompt())
        assertEquals(ConsentRepository.CURRENT_VERSION.toLong(), store.getLong(ConsentRepository.KEY_VERSION))
    }

    @Test
    fun necessaryOnlyIsADecisionWithNothingAllowed() = runTest {
        val state = repository().necessaryOnly()

        assertFalse(state.needsPrompt)
        assertFalse(state.analytics)
        assertFalse(state.crash)
        assertEquals(1_761_800_000_000L, state.decidedAtMillis)
    }

    @Test
    fun updateStoresEachChoiceAndTheNewTime() = runTest {
        val repository = repository()
        repository.acceptAll()
        clock.millis += 60_000

        val state = repository.update(analytics = true, crash = false)

        assertTrue(state.analytics)
        assertFalse(state.crash)
        assertEquals(1_761_800_060_000L, state.decidedAtMillis)
    }

    @Test
    fun olderVersionAsksAgainAndCountsAsNothingAllowed() = runTest {
        store.putLong(ConsentRepository.KEY_VERSION, 0)
        store.putLong(ConsentRepository.KEY_DECIDED_AT, 1L)
        store.putBoolean(ConsentRepository.KEY_ANALYTICS, true)
        store.putBoolean(ConsentRepository.KEY_CRASH, true)

        val state = repository().state()

        assertTrue(state.needsPrompt)
        assertFalse(state.analytics)
        assertFalse(state.crash)
        assertEquals(0, state.version)
    }

    @Test
    fun choicesWithoutADecisionTimeStillAsk() = runTest {
        // A write interrupted before the time was stored.
        store.putBoolean(ConsentRepository.KEY_ANALYTICS, true)

        assertTrue(repository().needsPrompt())
        assertFalse(repository().state().analytics)
    }
}
