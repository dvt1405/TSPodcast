package tss.t.shared.preferences

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** M4-AC8: Android `SharedPref.getOrCreateInstallId()`. */
class InstallIdTest {

    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test
    fun firstCallCreatesARandomUuidUnderTheAndroidKey() = runTest {
        val store = InMemoryKeyValueStore()

        val id = InstallId(store).getOrCreate()

        assertTrue(uuid.matches(id), "not a v4 UUID: $id")
        assertEquals("InstallId", InstallId.KEY_INSTALL_ID)
        assertEquals(id, store.getString("InstallId"))
    }

    @Test
    fun laterCallsAndNewInstancesReturnTheSameId() = runTest {
        val store = InMemoryKeyValueStore()
        val first = InstallId(store).getOrCreate()

        assertEquals(first, InstallId(store).getOrCreate())
        assertEquals(first, InstallId(store).getOrCreate())
    }

    @Test
    fun blankOrEmptyValueIsReplaced() = runTest {
        for (stored in listOf("", "   ")) {
            val store = InMemoryKeyValueStore()
            store.putString("InstallId", stored)

            val id = InstallId(store).getOrCreate()

            assertTrue(uuid.matches(id), "stored='$stored' gave $id")
            assertEquals(id, store.getString("InstallId"))
        }
    }

    @Test
    fun existingValueIsKeptAsIs() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("InstallId", "from-an-older-build")

        assertEquals("from-an-older-build", InstallId(store).getOrCreate())
    }

    @Test
    fun concurrentFirstCallsAgreeOnOneId() = runTest {
        val installId = InstallId(InMemoryKeyValueStore())

        val ids = coroutineScope { (1..20).map { async { installId.getOrCreate() } }.awaitAll() }

        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun twoInstallsGetDifferentIds() = runTest {
        assertNotEquals(
            InstallId(InMemoryKeyValueStore()).getOrCreate(),
            InstallId(InMemoryKeyValueStore()).getOrCreate(),
        )
    }
}
