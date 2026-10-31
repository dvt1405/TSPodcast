package tss.t.shared.preferences

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Random per-install identifier, port of Android `SharedPref.getOrCreateInstallId()`: key
 * [KEY_INSTALL_ID], a random UUID, created on first use and again whenever the stored value is
 * missing or blank. Used as the crash reporter's user id. It identifies an install, not a device
 * or a person: deleting the app forgets it (D5: no device identifier on iOS).
 */
class InstallId(private val store: KeyValueStore) {

    // Two first calls at the same time must not each create (and save) a different id.
    private val mutex = Mutex()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun getOrCreate(): String = mutex.withLock {
        store.getString(KEY_INSTALL_ID)?.takeIf { it.isNotBlank() }?.let { return@withLock it }
        Uuid.random().toString().also { store.putString(KEY_INSTALL_ID, it) }
    }

    companion object {
        /** Android `Key_InstallId`. */
        const val KEY_INSTALL_ID = "InstallId"
    }
}
