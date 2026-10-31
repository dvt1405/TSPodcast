package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import tss.t.shared.TestGraph
import tss.t.shared.model.PodcastByFeedIdRes
import tss.t.shared.model.TSDataState
import tss.t.shared.network.FakeCredentials
import tss.t.shared.network.MissingCredentialsException
import tss.t.shared.network.PodcastIndexHttpException
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RetryPolicyTest {

    private val episodes = """{"count":1,"items":[{"id":1,"feedId":42,"title":"Ep"}]}"""
    private val podcast = """{"feed":{"id":42,"title":"Show"},"query":{"id":"42"}}"""

    /** Answers with [statuses] in order, then keeps repeating the last one. */
    private fun scripted(body: String, vararg statuses: HttpStatusCode): TestGraph {
        var call = 0
        return TestGraph(handler = {
            val status = statuses[minOf(call++, statuses.lastIndex)]
            respondJson(if (status == HttpStatusCode.OK) body else """{"status":"false"}""", status)
        })
    }

    private fun TSDataState<*>.httpCode(): Int =
        assertIs<PodcastIndexHttpException>(assertIs<TSDataState.Error<*>>(this).exception).code

    @Test
    fun episodesByFeedRecoverAfterTwoFailures() = runTest {
        val test = scripted(
            episodes,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.OK,
        )

        val result = test.graph.getEpisodeByFeedId("42")

        assertIs<TSDataState.Success<*>>(result)
        assertEquals(3, test.requestsTo("episodes/byfeedid"))
    }

    @Test
    fun episodesByFeedGiveUpAfterThreeRetries() = runTest {
        val test = scripted(episodes, HttpStatusCode.InternalServerError)

        val result = test.graph.getEpisodeByFeedId("42")

        assertEquals(500, result.httpCode())
        assertEquals(4, test.requestsTo("episodes/byfeedid"))
    }

    @Test
    fun episodesByFeedRetryParseErrorsToo() = runTest {
        // Android: `retryWhen { attempt < 3 }` on E10, whatever the cause.
        val test = TestGraph(handler = { respondJson("not json") })

        val result = test.graph.getEpisodeByFeedId("42")

        assertIs<TSDataState.Error<*>>(result)
        assertEquals(4, test.requestsTo("episodes/byfeedid"))
    }

    @Test
    fun podcastByFeedGivesUpAfterThreeRetries() = runTest {
        val test = scripted(podcast, HttpStatusCode.ServiceUnavailable)

        val result = test.graph.getPodcastByFeedID("42")

        assertEquals(503, result.httpCode())
        assertEquals(4, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun podcastByFeedRecoversAfterOneFailure() = runTest {
        val test = scripted(podcast, HttpStatusCode.BadGateway, HttpStatusCode.OK)

        val result = test.graph.getPodcastByFeedID("42")

        assertEquals(42L, assertIs<TSDataState.Success<PodcastByFeedIdRes>>(result).data.feed?.id)
        assertEquals(2, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun podcastByFeedDoesNotRetryAnUnparseableBody() = runTest {
        val test = TestGraph(handler = { respondJson("""{"feed": {"id": "not a number"}""") })

        val result = test.graph.getPodcastByFeedID("42")

        assertIs<SerializationException>(assertIs<TSDataState.Error<*>>(result).exception)
        assertEquals(1, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun otherEndpointsMakeExactlyOneRequest() = runTest {
        val test = TestGraph(handler = { respondJson("{}", HttpStatusCode.InternalServerError) })
        val graph = test.graph

        assertEquals(500, graph.getTrendingPodcasts(max = 100, since = 1L).httpCode())
        assertEquals(500, graph.getLiveEpisodes().httpCode())
        assertEquals(500, graph.getRecentFeeds(cat = "1").httpCode())
        assertEquals(500, graph.getMusicFeeds().httpCode())
        assertEquals(500, graph.searchPodcasts("news").httpCode())
        assertEquals(500, graph.searchMusicFeeds("rock").httpCode())
        assertIs<TSDataState.Success<*>>(graph.getCategories()) // asset fallback

        listOf(
            "podcasts/trending",
            "episodes/live",
            "recent/feeds",
            "podcasts/bymedium",
            "search/byterm",
            "search/music/byterm",
            "categories/list",
        ).forEach { path -> assertEquals(1, test.requestsTo(path), path) }
        assertEquals(7, test.requests.size)
    }

    @Test
    fun missingCredentialsAreNeverRetried() = runTest {
        val test = TestGraph(credentials = FakeCredentials(apiKey = "\$(PODCASTINDEX_API_KEY)"))
        val graph = test.graph

        listOf(
            graph.getTrendingPodcasts(max = 100, since = 1_700_000_000L),
            graph.getEpisodeByFeedId("42"),
            graph.getPodcastByFeedID("42"),
            graph.getLiveEpisodes(),
            graph.getMusicFeeds(),
        ).forEach { result ->
            assertIs<MissingCredentialsException>(assertIs<TSDataState.Error<*>>(result).exception)
        }
        assertEquals(0, test.requests.size)
    }
}
