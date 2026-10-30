package tss.t.shared.detail

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** M6-AC9: the 1 000 episodes "Tìm trong show" searches, cached on their own key, with D6. */
class ShowEpisodeSearchTest {

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")

    private val episodesBody = """{"count":3,"items":[
        {"id":1,"feedId":42,"title":"Đọc truyện 1","explicit":0},
        {"id":2,"feedId":42,"title":"Explicit","explicit":1},
        {"id":3,"feedId":42,"title":"Đọc truyện 3","explicit":0}]}"""

    private fun feedBody(episodeCount: Int, explicit: Boolean = false) =
        """{"feed":{"id":42,"title":"Show","episodeCount":$episodeCount,"explicit":$explicit},"query":{"id":"42"}}"""

    private fun graph(feed: String = feedBody(3), fail: Boolean = false) = TestGraph(handler = { request ->
        when {
            fail && request.path() == "episodes/byfeedid" -> respondJson("{}", HttpStatusCode.InternalServerError)
            request.path() == "episodes/byfeedid" -> respondJson(episodesBody)
            else -> respondJson(feed)
        }
    })

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    private fun TestGraph.episodeRequests() = requests.filter { it.path() == "episodes/byfeedid" }

    @Test
    fun firstLoadAsksForAThousandEpisodesOnce() = runTest {
        val test = graph()
        val search = test.graph.showEpisodeSearch

        search.load(42)
        test.advance(14.minutes + 59.seconds)
        search.load(42)

        val requests = test.episodeRequests()
        assertEquals(1, requests.size, "the second load within 15 minutes is cached")
        assertEquals("1000", requests.single().url.parameters["max"])
        assertEquals("42", requests.single().url.parameters["id"])

        test.advance(2.seconds)
        search.load(42)
        assertEquals(2, test.episodeRequests().size, "15:01 later it asks again")
    }

    @Test
    fun detailKeepsItsOwnHundredEpisodeEntry() = runTest {
        val test = graph()

        test.graph.showEpisodeSearch.load(42)
        test.graph.podcastDetailFeed.load(42)
        test.graph.podcastDetailFeed.load(42)
        test.graph.showEpisodeSearch.load(42)

        assertEquals(listOf("1000", "100"), test.episodeRequests().map { it.url.parameters["max"] })
    }

    @Test
    fun explicitEpisodesAreDroppedByDefault() = runTest {
        val index = graph().graph.showEpisodeSearch.load(42).data()

        assertEquals(listOf(1L, 3L), index.episodes.map { it.id })
        assertEquals(3, index.loadedCount, "what E10 returned, before D6")
        assertEquals(listOf(1L, 3L), index.filter.filter("doc truyen").map { it.id })
    }

    @Test
    fun allowedExplicitContentKeepsThem() = runTest {
        val test = graph()
        test.graph.appPreferences.saveAllowExplicitContent(true)

        assertEquals(listOf(1L, 2L, 3L), test.graph.showEpisodeSearch.load(42).data().episodes.map { it.id })
    }

    @Test
    fun explicitFeedHasNothingToSearch() = runTest {
        val index = graph(feed = feedBody(3, explicit = true)).graph.showEpisodeSearch.load(42).data()

        assertTrue(index.episodes.isEmpty())
    }

    @Test
    fun truncatedWhenTheShowHasMoreEpisodesThanLoaded() = runTest {
        val big = graph(feed = feedBody(1_500)).graph.showEpisodeSearch.load(42).data()
        assertEquals(1_500, big.totalCount)
        assertTrue(big.isTruncated)

        val small = graph(feed = feedBody(3)).graph.showEpisodeSearch.load(42).data()
        assertFalse(small.isTruncated)

        val unknown = graph(feed = """{"feed":[],"query":{"id":"42"}}""").graph.showEpisodeSearch.load(42).data()
        assertNull(unknown.totalCount)
        assertFalse(unknown.isTruncated)
    }

    @Test
    fun errorIsReturnedAndNotCached() = runTest {
        val test = graph(fail = true)

        assertIs<TSDataState.Error<*>>(test.graph.showEpisodeSearch.load(42))
        val afterFirst = test.episodeRequests().size
        assertIs<TSDataState.Error<*>>(test.graph.showEpisodeSearch.load(42))

        assertEquals(afterFirst * 2, test.episodeRequests().size, "an error is not cached")
    }
}
