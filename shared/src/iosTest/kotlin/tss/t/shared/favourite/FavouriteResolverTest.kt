package tss.t.shared.favourite

import kotlinx.coroutines.test.runTest
import tss.t.shared.storage.TestDatabase
import tss.t.shared.storage.episode
import tss.t.shared.storage.podcast
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** M4-AC6: Android `SelectPodcastAndEpisodeByFavourite` + the selection of `FavouriteScreen`. */
class FavouriteResolverTest {
    private val test = TestDatabase()
    private val storage = test.storage
    private val resolver = FavouriteResolver(storage)

    @AfterTest
    fun tearDown() = test.close()

    private fun item(type: FavouriteType, id: String) = FavouriteItem(id, type, "T", null, null)

    private suspend fun savePodcast42() {
        storage.insertPodcast(podcast(42))
        // Saved out of order: the resolver sorts by datePublished, newest first.
        storage.insertEpisodes(
            listOf(episode(1, published = 100), episode(3, published = 300), episode(2, published = 200)),
        )
        storage.insertEpisode(episode(9, feedId = 7))
    }

    @Test
    fun episodeFavouriteGivesItsPodcastSortedEpisodesAndItsIndex() = runTest {
        savePodcast42()

        val resolved = assertNotNull(resolver.resolve(item(FavouriteType.PodcastEpisode, "2")))

        assertEquals(42, resolved.podcast.id)
        assertEquals(listOf(3L, 2L, 1L), resolved.episodes.map { it.id })
        assertEquals(1, resolved.startIndex)
    }

    @Test
    fun podcastFavouriteStartsAtTheNewestEpisode() = runTest {
        savePodcast42()

        val resolved = assertNotNull(resolver.resolve(FavouriteType.Podcast, "42"))

        assertEquals(42, resolved.podcast.id)
        assertEquals(listOf(3L, 2L, 1L), resolved.episodes.map { it.id })
        assertEquals(0, resolved.startIndex)
    }

    @Test
    fun podcastWithoutSavedEpisodesIsNull() = runTest {
        storage.insertPodcast(podcast(42))

        assertNull(resolver.resolve(FavouriteType.Podcast, "42"))
    }

    @Test
    fun episodeWhosePodcastRowIsGoneIsNull() = runTest {
        storage.insertEpisode(episode(1))

        assertNull(resolver.resolve(FavouriteType.PodcastEpisode, "1"))
    }

    @Test
    fun missingRowsAndNonNumericIdsAreNull() = runTest {
        savePodcast42()

        assertNull(resolver.resolve(FavouriteType.PodcastEpisode, "404"))
        assertNull(resolver.resolve(FavouriteType.Podcast, "404"))
        assertNull(resolver.resolve(FavouriteType.PodcastEpisode, "vov1"))
        assertNull(resolver.resolve(FavouriteType.Podcast, ""))
    }
}
