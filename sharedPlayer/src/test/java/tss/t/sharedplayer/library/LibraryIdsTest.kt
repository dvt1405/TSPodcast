package tss.t.sharedplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIdsTest {

    @Test
    fun podcastIdRoundTrips() {
        val id = LibraryIds.podcast(920666L)
        assertEquals("podcast:920666", id)
        assertEquals(920666L, LibraryIds.podcastIdOf(id))
    }

    @Test
    fun episodeIdsAreBareNumbersLikeThePhonePlayer() {
        // The phone player uses "${episode.id}"; positions and favourites key on it.
        assertEquals(41951637178L, LibraryIds.episodeIdOf("41951637178"))
        assertNull(LibraryIds.episodeIdOf(LibraryIds.podcast(1L)))
        assertNull(LibraryIds.episodeIdOf(LibraryIds.TRENDING))
    }

    @Test
    fun nodeIdsAreNotPodcastsOrEpisodes() {
        LibraryIds.ROOT_CHILDREN.forEach {
            assertNull(LibraryIds.podcastIdOf(it))
            assertNull(LibraryIds.episodeIdOf(it))
            assertTrue(LibraryIds.isRootChild(it))
        }
        assertNull(LibraryIds.podcastIdOf("podcast:abc"))
        assertFalse(LibraryIds.isRootChild(LibraryIds.ROOT))
    }

    @Test
    fun rootFitsTheAndroidAutoTabLimit() {
        assertTrue(LibraryIds.ROOT_CHILDREN.size <= 4)
    }

    @Test
    fun pagingSlicesAndStopsPastTheEnd() {
        val items = (1..25).toList()
        assertEquals((1..10).toList(), items.page(0, 10))
        assertEquals((21..25).toList(), items.page(2, 10))
        assertEquals(emptyList<Int>(), items.page(3, 10))
        assertEquals(emptyList<Int>(), items.page(-1, 10))
        assertEquals(emptyList<Int>(), items.page(0, 0))
        assertEquals(items, items.page(0, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), items.page(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
