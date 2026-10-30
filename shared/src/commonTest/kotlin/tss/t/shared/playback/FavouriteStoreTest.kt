package tss.t.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M3-AC2: favourites keyed by `(type, id)`, observable list. */
class FavouriteStoreTest {
    private val store = FavouriteStore()
    private fun episode(id: String) = FavouriteItem(id, FavouriteType.PodcastEpisode, "Episode $id", null, "Podcast")

    @Test
    fun toggleOnAndOff() {
        assertTrue(store.toggle(episode("1")))
        assertTrue(store.isFavourite(FavouriteType.PodcastEpisode, "1"))
        assertFalse(store.toggle(episode("1")))
        assertFalse(store.isFavourite(FavouriteType.PodcastEpisode, "1"))
    }

    @Test
    fun typeIsPartOfTheKey() {
        store.setFavourite(episode("1"), true)
        assertFalse(store.isFavourite(FavouriteType.Podcast, "1"))
        store.setFavourite(FavouriteItem("1", FavouriteType.Podcast, "P", null, null), true)
        assertEquals(2, store.items.value.size)
    }

    @Test
    fun itemsFollowEveryChange() {
        store.setFavourite(episode("1"), true)
        store.setFavourite(episode("2"), true)
        assertEquals(listOf("2", "1"), store.items.value.map { it.id })
        store.setFavourite(episode("1"), true)
        assertEquals(listOf("1", "2"), store.items.value.map { it.id })
        store.setFavourite(episode("2"), false)
        assertEquals(listOf("1"), store.items.value.map { it.id })
    }
}
