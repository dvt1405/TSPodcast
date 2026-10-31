package tss.t.shared.playback

import kotlinx.coroutines.test.runTest
import tss.t.shared.model.LiveEpisode
import tss.t.shared.preferences.InMemoryKeyValueStore
import tss.t.shared.storage.TestDatabase
import tss.t.shared.storage.episode
import tss.t.shared.storage.podcast
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * M4-AC5 (and M3-AC2): Android `HistoryRepository.saveCurrentPlaying` and `PlayerViewModel.playLive`.
 * The playlist and the podcast go to Room with `cachedAt = now`; previous/next only move the key.
 */
class PlaybackHistoryTest {
    private val test = TestDatabase()
    private val store = InMemoryKeyValueStore()
    private val history = PlaybackHistory(store, test.storage)
    private val now = TestDatabase.DAY_MS * 30

    @AfterTest
    fun tearDown() = test.close()

    @Test
    fun detailTapSavesThePlaylistThePodcastAndTheKey() = runTest {
        val playlist = listOf(episode(1), episode(2), episode(3))

        history.savePlaying(playlist[1], podcast(42), playlist)

        assertEquals("2", store.getString("extra:current_item_id"))
        assertEquals("2", history.currentItemId())
        playlist.forEach { assertEquals(now, test.db.episodeDao().selectById(it.id)?.cachedAt) }
        assertEquals(now, test.db.podcastDao().selectById(42)?.cachedAt)
        assertEquals(setOf(1L, 2L, 3L), test.storage.podcastWithEpisodes(42)?.episodes?.map { it.id }?.toSet())
    }

    @Test
    fun withoutAPodcastOnlyTheEpisodesAreSaved() = runTest {
        history.savePlaying(episode(1), podcast = null, playlist = listOf(episode(1)))

        assertEquals(1, test.count("Episode"))
        assertEquals(0, test.count("Podcast"))
        assertEquals("1", history.currentItemId())
    }

    @Test
    fun liveTapSavesEveryLiveItemAsAnEpisodeAndAPodcastOfTheFeed() = runTest {
        val first = LiveEpisode(id = 501, feedId = 77, title = "Morning live", feedImage = "https://example.com/f.png", image = "https://example.com/l.png", chaptersUrl = "https://example.com/ch.json")
        val second = LiveEpisode(id = 502, feedId = 78, title = "Evening live", feedImage = "https://example.com/g.png")

        history.saveLive(second, listOf(first, second))

        assertEquals("502", history.currentItemId())
        val saved = assertNotNull(test.storage.episodeById(501))
        assertEquals(77, saved.feedId)
        assertEquals("https://example.com/f.png", saved.image, "Episode.fromLive uses the feed image")
        assertEquals("https://example.com/ch.json", saved.feedUrl, "Episode.fromLive copies chaptersUrl like Android")
        assertNotNull(test.storage.episodeById(502))
        val podcast = assertNotNull(test.storage.podcastById(78))
        assertEquals(78, podcast.feedId)
        assertEquals("Evening live", podcast.title)
        assertEquals("Evening live", podcast.feedTitle)
        assertNull(test.storage.podcastById(77), "only the podcast of the tapped item, as playLive")
    }

    @Test
    fun previousAndNextOnlyChangeTheKey() = runTest {
        history.saveCurrentId("7")
        history.saveCurrentId("8")

        assertEquals("8", history.currentItemId())
        assertEquals(0, test.count("Episode"))
        assertEquals(0, test.count("Podcast"))
    }

    @Test
    fun replayingLaterRestampsThePlaylist() = runTest {
        history.savePlaying(episode(1), podcast(42), listOf(episode(1)))
        test.clock.millis += 60_000

        history.savePlaying(episode(1), podcast(42), listOf(episode(1)))

        assertEquals(now + 60_000, test.db.episodeDao().selectById(1)?.cachedAt)
        assertEquals(now + 60_000, test.db.podcastDao().selectById(42)?.cachedAt)
    }
}
