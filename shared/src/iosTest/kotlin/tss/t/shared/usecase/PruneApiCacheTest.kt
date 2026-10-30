package tss.t.shared.usecase

import kotlinx.coroutines.test.runTest
import tss.t.shared.crash.FakeCrashReporter
import tss.t.shared.favourite.FavouriteItem
import tss.t.shared.favourite.FavouriteRepository
import tss.t.shared.favourite.FavouriteType
import tss.t.shared.model.Feed
import tss.t.shared.storage.PodcastDao
import tss.t.shared.storage.PodcastDaos
import tss.t.shared.storage.TestDatabase
import tss.t.shared.storage.TestDatabase.Companion.DAY_MS
import tss.t.shared.storage.episode
import tss.t.shared.storage.podcast
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M4-AC4: Android `PruneApiCache` (7 days, keep favourites, log, swallow errors) plus the
 * [Sửa] that keeps the podcast of a favourited episode. "now" is day 30.
 */
class PruneApiCacheTest {
    private val test = TestDatabase(startMillis = 30 * DAY_MS)
    private val storage = test.storage
    private val crash = FakeCrashReporter()
    private val prune = PruneApiCache(test.db, test.clock, crash)
    private val favourites = FavouriteRepository(test.db.favouriteDao())

    @AfterTest
    fun tearDown() = test.close()

    /** Runs [block] with the clock set [daysAgo] days before now, then restores it. */
    private suspend fun writtenDaysAgo(daysAgo: Int, block: suspend () -> Unit) {
        val now = test.clock.millis
        test.clock.millis = now - daysAgo * DAY_MS
        block()
        test.clock.millis = now
    }

    private suspend fun favourite(type: FavouriteType, id: Long) =
        favourites.setFavourite(FavouriteItem(id.toString(), type, "T", null, null), true)

    @Test
    fun ttlIsSevenDays() {
        assertEquals(7 * DAY_MS, PruneApiCache.TTL_MS)
    }

    @Test
    fun rowsOlderThanSevenDaysGoAndYoungerOnesStay() = runTest {
        writtenDaysAgo(8) {
            storage.insertPodcast(podcast(1))
            storage.insertEpisode(episode(10, feedId = 1))
            storage.insertFeed(Feed(id = 1))
        }
        writtenDaysAgo(6) {
            storage.insertPodcast(podcast(2))
            storage.insertEpisode(episode(20, feedId = 2))
            storage.insertFeed(Feed(id = 2))
        }

        val result = prune()

        assertEquals(PruneResult(podcasts = 1, episodes = 1, feeds = 1), result)
        assertNull(storage.podcastById(1))
        assertNull(storage.episodeById(10))
        assertNull(storage.feedById(1))
        assertNotNull(storage.podcastById(2))
        assertNotNull(storage.episodeById(20))
        assertNotNull(storage.feedById(2))
        assertEquals(listOf("PruneApiCache: removed 1 podcasts, 1 episodes, 1 feeds"), crash.logs)
    }

    @Test
    fun aFavouritedEpisodeStays() = runTest {
        writtenDaysAgo(8) { storage.insertEpisodes(listOf(episode(10, feedId = 1), episode(11, feedId = 1))) }
        favourite(FavouriteType.PodcastEpisode, 10)

        prune()

        assertNotNull(storage.episodeById(10))
        assertNull(storage.episodeById(11), "other episodes of the feed expire like on Android")
    }

    @Test
    fun aFavouritedPodcastKeepsItselfAndItsEpisodes() = runTest {
        writtenDaysAgo(8) {
            storage.insertPodcast(podcast(1))
            storage.insertEpisodes(listOf(episode(10, feedId = 1), episode(11, feedId = 1)))
            storage.insertFeed(Feed(id = 1))
        }
        favourite(FavouriteType.Podcast, 1)

        val result = prune()

        assertEquals(PruneResult(0, 0, 0), result)
        assertNotNull(storage.podcastById(1))
        assertNotNull(storage.episodeById(10))
        assertNotNull(storage.episodeById(11))
        assertNotNull(storage.feedById(1))
    }

    @Test
    fun thePodcastOfAFavouritedEpisodeStays() = runTest {
        // [Sửa]: Android deletes podcast 1 here, so the favourite cannot open offline.
        writtenDaysAgo(8) {
            storage.insertPodcast(podcast(1))
            storage.insertPodcast(podcast(2))
            storage.insertEpisodes(listOf(episode(10, feedId = 1), episode(20, feedId = 2)))
        }
        favourite(FavouriteType.PodcastEpisode, 10)

        val result = prune()

        assertNotNull(storage.podcastById(1))
        assertNotNull(storage.episodeById(10))
        assertNull(storage.podcastById(2))
        assertNull(storage.episodeById(20))
        assertEquals(PruneResult(podcasts = 1, episodes = 1, feeds = 0), result)
        assertNotNull(storage.podcastWithEpisodes(1)?.episodes?.singleOrNull { it.id == 10L })
    }

    @Test
    fun nothingRemovedLogsNothing() = runTest {
        writtenDaysAgo(1) { storage.insertPodcast(podcast(1)) }

        assertEquals(PruneResult(0, 0, 0), prune())
        assertTrue(crash.logs.isEmpty())
        assertTrue(crash.records.isEmpty())
    }

    @Test
    fun aDatabaseErrorIsReportedAndSwallowed() = runTest {
        val failing = object : PodcastDaos by test.db {
            override fun podcastDao(): PodcastDao = throw IllegalStateException("disk I/O error")
        }

        val result = PruneApiCache(failing, test.clock, crash)()

        assertEquals(PruneResult(0, 0, 0), result)
        val record = crash.records.single()
        assertEquals(PruneApiCache.TAG, record.tag)
        assertEquals("disk I/O error", record.throwable.message)
        assertTrue(crash.logs.isEmpty())
    }
}
