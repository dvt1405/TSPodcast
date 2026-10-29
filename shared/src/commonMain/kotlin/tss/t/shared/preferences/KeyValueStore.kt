package tss.t.shared.preferences

/**
 * Small persistent key-value store, the shared stand-in for Android's `SharedPref`
 * (architecture §3.3). Values are typed by the getter used to read them: reading a key with
 * another type than the one it was written with returns null.
 *
 * Implementations are best effort and never throw (except `CancellationException`): an
 * unreadable store reads as empty and a failed write is dropped, so a broken disk can never
 * break a screen. Every operation is `suspend` and safe to call from any coroutine.
 */
interface KeyValueStore {
    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String)

    suspend fun getBoolean(key: String): Boolean?

    suspend fun putBoolean(key: String, value: Boolean)

    suspend fun getLong(key: String): Long?

    suspend fun putLong(key: String, value: Long)

    suspend fun remove(key: String)

    /** Removes every key. */
    suspend fun clear()
}
