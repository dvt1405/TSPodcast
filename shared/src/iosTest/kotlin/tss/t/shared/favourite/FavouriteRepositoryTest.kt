package tss.t.shared.favourite

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tss.t.shared.storage.TestDatabase
import tss.t.shared.storage.awaitValue
import tss.t.shared.storage.into
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Favourites in Room keyed by `(type, id)`, observable, newest first (replaces M3 FavouriteStore). */
class FavouriteRepositoryTest {
    private val test = TestDatabase()
    private val repository = FavouriteRepository(test.db.favouriteDao())

    @AfterTest
    fun tearDown() = test.close()

    private fun episode(id: String) = FavouriteItem(id, FavouriteType.PodcastEpisode, "Episode $id", "https://example.com/$id.png", "Podcast")

    @Test
    fun toggleOnAndOff() = runTest {
        assertTrue(repository.toggle(episode("1")))
        assertTrue(repository.isFavourite(FavouriteType.PodcastEpisode, "1"))
        assertEquals(1, repository.count())
        assertFalse(repository.toggle(episode("1")))
        assertFalse(repository.isFavourite(FavouriteType.PodcastEpisode, "1"))
        assertEquals(0, repository.count())
    }

    @Test
    fun typeIsPartOfTheKey() = runTest {
        repository.setFavourite(episode("1"), true)
        assertFalse(repository.isFavourite(FavouriteType.Podcast, "1"))
        repository.setFavourite(FavouriteItem("1", FavouriteType.Podcast, "P", null, null), true)
        assertEquals(2, repository.count())
    }

    @Test
    fun itemsKeepEveryFieldAndFollowEveryChange() = runTest {
        val values = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch { repository.items.map { items -> items.map { it.id } }.into(values) }

        values.awaitValue(emptyList())
        repository.setFavourite(episode("1"), true)
        values.awaitValue(listOf("1"))
        repository.setFavourite(episode("2"), true)
        values.awaitValue(listOf("2", "1"))
        repository.setFavourite(episode("2"), false)
        values.awaitValue(listOf("1"))
        job.cancel()

        assertEquals(listOf(episode("1")), repository.items.first())
    }
}
