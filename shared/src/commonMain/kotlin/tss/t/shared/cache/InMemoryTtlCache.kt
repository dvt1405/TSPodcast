package tss.t.shared.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.shared.util.EpochClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory cache whose entries expire [ttl] after they were stored.
 *
 * Replaces the `@Volatile` fields, `ConcurrentHashMap`s and `synchronized` blocks that
 * Android keeps in use case companion objects (architecture §3.4). One instance per use case
 * lives in [tss.t.shared.SharedGraph], so the cache is a singleton without being static, and
 * tests get a fresh one with a fake [clock].
 *
 * An entry is fresh while `now - storedAt < ttl`, the same strict comparison as Android.
 */
class InMemoryTtlCache<K : Any, V : Any>(
    private val ttl: Duration,
    private val clock: EpochClock = EpochClock.System,
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<V>>()

    /** The value stored under [key], or null when there is none or it has expired. */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = entries[key] ?: return@withLock null
        if (entry.isFresh(clock.nowMillis())) {
            entry.value
        } else {
            entries.remove(key)
            null
        }
    }

    suspend fun put(key: K, value: V) {
        mutex.withLock { entries[key] = Entry(value, clock.nowMillis()) }
    }

    suspend fun remove(key: K) {
        mutex.withLock { entries.remove(key) }
    }

    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    private inner class Entry<T>(val value: T, private val storedAtMillis: Long) {
        fun isFresh(nowMillis: Long): Boolean =
            ttl.isInfinite() || nowMillis - storedAtMillis < ttl.inWholeMilliseconds
    }

    companion object {
        /** TTL of the trending, episodes-by-feed and podcast-by-feed caches on Android. */
        val API_CACHE_TTL: Duration = 15.minutes
    }
}
