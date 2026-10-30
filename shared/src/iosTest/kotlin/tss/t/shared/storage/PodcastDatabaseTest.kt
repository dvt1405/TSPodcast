package tss.t.shared.storage

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tss.t.shared.favourite.FavouriteType
import tss.t.shared.model.Feed
import tss.t.shared.model.Person
import tss.t.shared.model.Soundbite
import tss.t.shared.model.Value
import tss.t.shared.model.ValueModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** M4-AC3: DAOs on an in-memory Room database with a fake clock (Android DAO behaviour). */
class PodcastDatabaseTest {
    private val test = TestDatabase()
    private val storage = test.storage

    @AfterTest
    fun tearDown() = test.close()

    private fun favourite(id: String, type: FavouriteType = FavouriteType.PodcastEpisode) =
        FavouriteEntity(id = id, type = type, title = "T$id", image = null, description = null)

    @Test
    fun insertsStampCachedAtWithTheClock() = runTest {
        storage.insertPodcast(podcast(42))
        storage.insertEpisodes(listOf(episode(1), episode(2)))
        storage.insertFeed(Feed(id = 42, title = "Feed"))
        test.clock.millis += 5_000
        storage.insertEpisode(episode(3))

        val start = TestDatabase.DAY_MS * 30
        assertEquals(start, test.db.podcastDao().selectById(42)?.cachedAt)
        assertEquals(start, test.db.episodeDao().selectById(1)?.cachedAt)
        assertEquals(start, test.db.episodeDao().selectById(2)?.cachedAt)
        assertEquals(start + 5_000, test.db.episodeDao().selectById(3)?.cachedAt)
        assertEquals(start, test.db.feedDao().selectById(42)?.cachedAt)
    }

    @Test
    fun insertingTheSameIdReplacesTheRowAndRestampsIt() = runTest {
        storage.insertEpisode(episode(1).copy(title = "Old"))
        storage.insertPodcast(podcast(42, title = "Old"))
        test.clock.millis += 1_000
        storage.insertEpisode(episode(1).copy(title = "New"))
        storage.insertPodcast(podcast(42, title = "New"))

        assertEquals(1, test.count("Episode"))
        assertEquals(1, test.count("Podcast"))
        val row = assertNotNull(test.db.episodeDao().selectById(1))
        assertEquals("New", row.title)
        assertEquals(TestDatabase.DAY_MS * 30 + 1_000, row.cachedAt)
        assertEquals("New", storage.podcastById(42)?.title)
    }

    @Test
    fun selectAllEpisodeByIdReturnsThePodcastAndOnlyItsEpisodes() = runTest {
        storage.insertPodcast(podcast(42))
        storage.insertPodcast(podcast(7))
        storage.insertEpisodes(listOf(episode(1), episode(2), episode(3, feedId = 7)))

        val saved = assertNotNull(test.db.podcastDao().selectAllEpisodeById(42))

        assertEquals(42, saved.podcast.id)
        assertEquals(setOf(1L, 2L), saved.episodes.map { it.id }.toSet())
        assertNull(test.db.podcastDao().selectAllEpisodeById(99))
    }

    @Test
    fun episodesOfAFeedAreNewestFirst() = runTest {
        storage.insertEpisodes(listOf(episode(1, published = 300), episode(2, published = 100), episode(3, published = 200)))

        assertEquals(listOf(1L, 3L, 2L), storage.episodesOfFeed(42).map { it.id })
        assertEquals(emptyList(), storage.episodesOfFeed(7))
    }

    @Test
    fun rowsKeepEveryApiField() = runTest {
        val rich = episode(1).copy(
            feedItunesId = null,
            persons = listOf(Person(name = "Host", role = "host")),
            soundbite = Soundbite(duration = 30.5, startTime = 73.0, title = "Clip"),
            soundbites = listOf(Soundbite(duration = 10.0)),
            value = Value(model = ValueModel(method = "keysend", type = "lightning")),
        )
        storage.insertEpisode(rich)
        storage.insertPodcast(podcast(42).copy(categories = mapOf("1" to "Arts"), feedId = null))

        assertEquals(rich.copy(feedItunesId = 0L), storage.episodeById(1))
        val podcast = assertNotNull(storage.podcastById(42))
        assertEquals(mapOf("1" to "Arts"), podcast.categories)
        assertEquals(42, podcast.feedId, "feedId is NOT NULL on Android: the podcast id stands in")
    }

    @Test
    fun observeAllIsNewestFirstAndFollowsInsertAndDelete() = runTest {
        val dao = test.db.favouriteDao()
        val values = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch { dao.observeAll().map { rows -> rows.map { it.id } }.into(values) }

        values.awaitValue(emptyList())
        dao.insert(favourite("1"))
        values.awaitValue(listOf("1"))
        dao.insert(favourite("2"))
        values.awaitValue(listOf("2", "1"))
        dao.insert(favourite("1"))
        values.awaitValue(listOf("1", "2"))
        assertEquals(1, dao.delete(FavouriteType.PodcastEpisode, "2"))
        values.awaitValue(listOf("1"))

        job.cancel()
        assertEquals(1, dao.count())
    }

    @Test
    fun selectByIdTellsTypesApart() = runTest {
        val dao = test.db.favouriteDao()
        dao.insert(favourite("42", FavouriteType.PodcastEpisode))

        assertNotNull(dao.selectById("42", FavouriteType.PodcastEpisode))
        assertNull(dao.selectById("42", FavouriteType.Podcast))

        dao.insert(favourite("42", FavouriteType.Podcast))
        assertEquals(2, dao.count())
        assertEquals(0, dao.delete(FavouriteType.Podcast, "7"))
        assertEquals(1, dao.delete(FavouriteType.Podcast, "42"))
        assertNotNull(dao.selectById("42", FavouriteType.PodcastEpisode))
    }
}
