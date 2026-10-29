package tss.t.shared.preferences

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.Podcast
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.PodcastIndexJson
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M1-AC1: the DataStore file store and the Android key names. */
class AppPreferencesTest {

    private val fs = FileSystem.SYSTEM
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ts_prefs_test_${Random.nextLong().toULong()}"
    private val file: Path = dir / DataStoreKeyValueStore.FILE_NAME
    private val opened = mutableListOf<DataStoreKeyValueStore>()

    private fun open(): DataStoreKeyValueStore = DataStoreKeyValueStore(file.toString()).also { opened += it }

    private fun prefs(store: KeyValueStore) = AppPreferences(store, PodcastIndexJson)

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        fs.deleteRecursively(dir)
    }

    private val categories = CategoryRes(
        count = 2,
        feeds = listOf(CategoryRes.Category(1, "Arts"), CategoryRes.Category(55, "News")),
    )
    private val trending = TrendingPodcastRes(count = 1, items = listOf(Podcast(id = 7, title = "Show")))

    @Test
    fun defaultsBeforeAnyWrite() = runTest {
        val prefs = prefs(open())

        assertFalse(prefs.isOnboardingFinished())
        assertFalse(prefs.hasSelectFavouriteCategory())
        assertNull(prefs.favouriteCategories())
        assertNull(prefs.categoryRes())
        assertNull(prefs.cachedTrending(null))
        assertNull(prefs.cachedTrending("1,2,3"))
        assertFalse(prefs.allowExplicitContent())
    }

    @Test
    fun everyKeyIsReadBackByANewInstanceOnTheSameFile() = runTest {
        val first = open()
        prefs(first).apply {
            saveOnboardingFinished(true)
            saveFavouriteCategories(listOf(CategoryRes.Category(1, "Arts", isFavourite = true)))
            saveCategoryRes(categories)
            saveTrending(null, trending, savedAtMillis = 111L)
            saveTrending("1,2,3", trending, savedAtMillis = 222L)
            saveAllowExplicitContent(true)
        }
        first.closeAndJoin()

        val second = open()
        val prefs = prefs(second)
        assertTrue(prefs.isOnboardingFinished())
        assertTrue(prefs.hasSelectFavouriteCategory())
        assertEquals(listOf(CategoryRes.Category(1, "Arts", isFavourite = true)), prefs.favouriteCategories())
        assertEquals(categories, prefs.categoryRes())
        assertEquals(AppPreferences.CachedTrending(trending, 111L), prefs.cachedTrending(null))
        assertEquals(AppPreferences.CachedTrending(trending, 222L), prefs.cachedTrending("1,2,3"))
        assertTrue(prefs.allowExplicitContent())

        // Android's key names, read through the raw store.
        assertEquals(true, second.getBoolean("OnboardingFinished"))
        assertEquals(true, second.getBoolean("HasSelectFavouriteCategory"))
        assertTrue(second.getString("ListFavouriteCategory")!!.contains("\"isFavourite\":true"))
        assertTrue(second.getString("CategoryRes")!!.contains("\"News\""))
        assertEquals(111L, second.getLong("lastUpdateTrending"))
        assertEquals(222L, second.getLong("lastUpdateTrending_1,2,3"))
        assertTrue(second.getString("cachedTrending")!!.contains("\"Show\""))
        assertTrue(second.getString("cachedTrending_1,2,3")!!.contains("\"Show\""))
        assertEquals(true, second.getBoolean("AllowExplicitContent"))
    }

    @Test
    fun corruptFileReadsAsDefaultsWithoutThrowing() = runTest {
        fs.createDirectories(dir)
        fs.write(file) { write(ByteArray(64) { 0xFF.toByte() }) }

        val store = open()
        val prefs = prefs(store)
        assertFalse(prefs.isOnboardingFinished())
        assertNull(prefs.categoryRes())

        // Still usable afterwards.
        prefs.saveOnboardingFinished(true)
        assertTrue(prefs.isOnboardingFinished())
    }

    @Test
    fun hundredConcurrentWritesLoseNothing() = runTest {
        val store = open()
        coroutineScope {
            (0 until 100).map { i -> async { store.putLong("key_$i", i.toLong()) } }.awaitAll()
        }
        store.closeAndJoin()

        val reopened = open()
        (0 until 100).forEach { i -> assertEquals(i.toLong(), reopened.getLong("key_$i"), "key_$i") }
    }

    @Test
    fun clearEmptiesTheStore() = runTest {
        val store = open()
        val prefs = prefs(store)
        prefs.saveOnboardingFinished(true)
        prefs.saveCategoryRes(categories)
        store.putString("other", "x")

        prefs.clear()

        assertFalse(prefs.isOnboardingFinished())
        assertNull(prefs.categoryRes())
        assertNull(store.getString("other"))
    }

    @Test
    fun wrongTypeAndBadJsonReadAsNull() = runTest {
        val store = open()
        store.putString("OnboardingFinished", "yes")
        store.putString("CategoryRes", "{not json")
        store.putBoolean("lastUpdateTrending", true)

        val prefs = prefs(store)
        assertFalse(prefs.isOnboardingFinished())
        assertNull(prefs.categoryRes())
        assertNull(prefs.cachedTrending(null))
    }

    @Test
    fun removeDeletesOneKey() = runTest {
        val store = open()
        store.putBoolean("a", true)
        store.putString("b", "x")

        store.remove("a")

        assertNull(store.getBoolean("a"))
        assertEquals("x", store.getString("b"))
    }

    @Test
    fun missingParentDirectoryIsCreated() = runTest {
        val nested = (dir / "nested" / DataStoreKeyValueStore.FILE_NAME).toString().toPath()
        val store = DataStoreKeyValueStore(nested.toString()).also { opened += it }

        store.putBoolean("x", true)

        assertTrue(fs.exists(nested))
    }
}
