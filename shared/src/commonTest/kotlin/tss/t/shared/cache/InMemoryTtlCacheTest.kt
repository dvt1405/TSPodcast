package tss.t.shared.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tss.t.shared.network.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InMemoryTtlCacheTest {

    private val clock = FixedClock(1_700_000_000_000L)
    private val cache = InMemoryTtlCache<String, String>(15.minutes, clock)

    private fun advance(by: Duration) {
        clock.millis += by.inWholeMilliseconds
    }

    @Test
    fun missingKeyIsNull() = runTest {
        assertNull(cache.get("a"))
    }

    @Test
    fun entryIsFreshUntilTheTtlHasPassed() = runTest {
        cache.put("a", "A")

        advance(14.minutes + 59.seconds)
        assertEquals("A", cache.get("a"))

        advance(1.seconds) // exactly 15 minutes: expired, `now - storedAt < ttl` like Android
        assertNull(cache.get("a"))
    }

    @Test
    fun entryExpiresAfterFifteenMinutes() = runTest {
        cache.put("a", "A")

        advance(15.minutes + 1.seconds)

        assertNull(cache.get("a"))
    }

    @Test
    fun keysExpireIndependently() = runTest {
        cache.put("a", "A")
        advance(10.minutes)
        cache.put("b", "B")
        advance(6.minutes)

        assertNull(cache.get("a"))
        assertEquals("B", cache.get("b"))
    }

    @Test
    fun putRestartsTheTtl() = runTest {
        cache.put("a", "old")
        advance(10.minutes)
        cache.put("a", "new")
        advance(10.minutes)

        assertEquals("new", cache.get("a"))
    }

    @Test
    fun removeAndClear() = runTest {
        cache.put("a", "A")
        cache.put("b", "B")

        cache.remove("a")
        assertNull(cache.get("a"))
        assertEquals("B", cache.get("b"))

        cache.clear()
        assertNull(cache.get("b"))
    }

    @Test
    fun infiniteTtlNeverExpires() = runTest {
        val forever = InMemoryTtlCache<Unit, String>(Duration.INFINITE, clock)
        forever.put(Unit, "value")

        advance(3650.days)

        assertEquals("value", forever.get(Unit))
    }

    @Test
    fun concurrentWritesAreAllKept() = runTest {
        val shared = InMemoryTtlCache<Int, Int>(15.minutes, clock)

        coroutineScope {
            repeat(200) { i -> launch(Dispatchers.Default) { shared.put(i, i * 2) } }
        }

        repeat(200) { i -> assertEquals(i * 2, shared.get(i)) }
    }
}
