package tss.t.shared.preferences

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [KeyValueStore] in a map, shared by several test graphs to stand for "the same file". */
class InMemoryKeyValueStore(initial: Map<String, Any> = emptyMap()) : KeyValueStore {
    private val mutex = Mutex()
    private val values = initial.toMutableMap()

    /** Raw view for assertions. */
    suspend fun snapshot(): Map<String, Any> = mutex.withLock { values.toMap() }

    override suspend fun getString(key: String): String? = get(key)
    override suspend fun putString(key: String, value: String) = put(key, value)
    override suspend fun getBoolean(key: String): Boolean? = get(key)
    override suspend fun putBoolean(key: String, value: Boolean) = put(key, value)
    override suspend fun getLong(key: String): Long? = get(key)
    override suspend fun putLong(key: String, value: Long) = put(key, value)
    override suspend fun remove(key: String) = mutex.withLock { values.remove(key); Unit }
    override suspend fun clear() = mutex.withLock { values.clear() }

    private suspend inline fun <reified T> get(key: String): T? = mutex.withLock { values[key] as? T }
    private suspend fun put(key: String, value: Any) = mutex.withLock { values[key] = value }
}
