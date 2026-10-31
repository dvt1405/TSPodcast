package tss.t.shared.preferences

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import tss.t.shared.model.CategoryRes
import tss.t.shared.network.PodcastIndexJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M1-AC3: at least 3 categories, stored like `SharedPref.saveFavouriteCategory`. */
class FavouriteCategoriesTest {

    private val store = InMemoryKeyValueStore()
    private val prefs = AppPreferences(store, PodcastIndexJson)
    private val favourites = FavouriteCategories(prefs)

    private fun category(id: Int) = CategoryRes.Category(id = id, name = "Cat $id")

    @Test
    fun canSaveFromThree() {
        (0..2).forEach { assertFalse(favourites.canSave(it), "canSave($it)") }
        assertTrue(favourites.canSave(3))
        assertTrue(favourites.canSave(10))
        assertEquals(3, FavouriteCategories.MIN_FAVOURITE_CATEGORIES)
    }

    @Test
    fun savingTwoWritesNothing() = runTest {
        assertFalse(favourites.save(listOf(category(1), category(2))))

        assertFalse(prefs.hasSelectFavouriteCategory())
        assertNull(store.getString(AppPreferences.KEY_LIST_FAVOURITE_CATEGORY))
        assertTrue(store.snapshot().isEmpty())
        assertNull(favourites.favouriteCategoryIds())
    }

    @Test
    fun duplicatesDoNotCountTowardsTheMinimum() = runTest {
        assertFalse(favourites.save(listOf(category(1), category(1), category(2))))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun savingThreeStoresThemAsFavourites() = runTest {
        assertTrue(favourites.save(listOf(category(5), category(1), category(3))))

        assertTrue(prefs.hasSelectFavouriteCategory())
        val raw = store.getString(AppPreferences.KEY_LIST_FAVOURITE_CATEGORY)!!
        val stored = PodcastIndexJson.decodeFromString(ListSerializer(CategoryRes.Category.serializer()), raw)
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.isFavourite == true })
        assertEquals(setOf(1, 3, 5), stored.map { it.id }.toSet())
        assertEquals("1,3,5", favourites.favouriteCategoryIds())
        assertEquals(listOf(1, 3, 5), favourites.favourites().map { it.id })
    }
}
