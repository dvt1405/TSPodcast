package tss.t.shared.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import tss.t.shared.util.typeName

/**
 * [KeyValueStore] on one DataStore Preferences file (M1 §4.1): `ts_prefs.preferences_pb`,
 * protobuf, written atomically by DataStore (temp file + rename). A plain file instead of the
 * system defaults database, so iOS needs no required-reason entry for it in
 * `PrivacyInfo.xcprivacy`.
 *
 * A file that cannot be parsed is replaced by an empty store (logged once, never thrown).
 *
 * DataStore allows **one** active instance per file and process: keep one store per path
 * (the [tss.t.shared.SharedGraph] owns it) and [close] it before opening the file again.
 *
 * @param path absolute path of the store file; it must end with `.preferences_pb`. Missing
 *   parent directories are created on the first write.
 */
class DataStoreKeyValueStore(path: String) : KeyValueStore {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { error ->
            log("store file is corrupt (${error.typeName}), starting empty")
            emptyPreferences()
        },
        scope = scope,
        produceFile = { path.toPath() },
    )

    override suspend fun getString(key: String): String? =
        read()[stringPreferencesKey(key)] as? String

    override suspend fun putString(key: String, value: String) =
        write { it[stringPreferencesKey(key)] = value }

    override suspend fun getBoolean(key: String): Boolean? =
        read()[booleanPreferencesKey(key)] as? Boolean

    override suspend fun putBoolean(key: String, value: Boolean) =
        write { it[booleanPreferencesKey(key)] = value }

    override suspend fun getLong(key: String): Long? =
        read()[longPreferencesKey(key)] as? Long

    override suspend fun putLong(key: String, value: Long) =
        write { it[longPreferencesKey(key)] = value }

    override suspend fun remove(key: String) = write { prefs ->
        prefs.asMap().keys.filter { it.name == key }.forEach { prefs.remove(it) }
    }

    override suspend fun clear() = write { it.clear() }

    /** Stops DataStore so the same file can be opened again. The store is unusable afterwards. */
    fun close() {
        job.cancel()
    }

    /** [close], then waits until DataStore has released the file (tests reopen it right away). */
    internal suspend fun closeAndJoin() {
        job.cancelAndJoin()
    }

    /**
     * The whole map; values are looked up untyped because `Preferences.get` casts without
     * checking. `Preferences.Key` equality is by name only.
     */
    private suspend fun read(): Map<Preferences.Key<*>, Any> = try {
        dataStore.data.first().asMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("read failed (${e.typeName})")
        emptyMap()
    }

    private suspend fun write(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("write failed (${e.typeName})")
        }
    }

    private fun log(message: String) {
        println("TSPodcast/KeyValueStore: $message")
    }

    companion object {
        /** File name the apps use, in Application Support on iOS. */
        const val FILE_NAME = "ts_prefs.preferences_pb"
    }
}
