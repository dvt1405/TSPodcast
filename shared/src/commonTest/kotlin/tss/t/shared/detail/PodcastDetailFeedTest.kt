package tss.t.shared.detail

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexHttpException
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** M2-AC1: `PodcastViewModel.getEpisodes` (E10 ‖ E11, E10 decides), retry, cache and D6. */
class PodcastDetailFeedTest {

    private val episodesBody = """{"count":3,"items":[
        {"id":1,"feedId":42,"title":"Ep 1","explicit":0},
        {"id":2,"feedId":42,"title":"Ep 2","explicit":1},
        {"id":3,"feedId":42,"title":"Ep 3"}]}"""
    private val cleanFeedBody = """{"feed":{"id":42,"title":"Show","explicit":false},"query":{"id":"42"}}"""
    private val explicitFeedBody = """{"feed":{"id":42,"title":"Show","explicit":true},"query":{"id":"42"}}"""
    private val serverError = """{"status":"false"}"""

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")
    private fun HttpRequestData.param(name: String) = url.parameters[name]

    private fun TestGraph.e10() = requestsTo(EPISODES)
    private fun TestGraph.e11() = requestsTo(PODCAST)

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    /** E10 answers [episodes] (or 500 when null), E11 answers [podcast] (or 500 when null). */
    private fun graph(
        episodes: String? = episodesBody,
        podcast: String? = cleanFeedBody,
    ) = TestGraph(handler = { request ->
        val body = if (request.path() == EPISODES) episodes else podcast
        if (body == null) respondJson(serverError, HttpStatusCode.InternalServerError) else respondJson(body)
    })

    private suspend fun TestGraph.allowExplicit() {
        graph.appPreferences.saveAllowExplicitContent(true)
    }

    @Test
    fun requestsUseAndroidParameters() = runTest {
        val test = graph()
        test.allowExplicit()

        test.graph.podcastDetailFeed.load(42)

        assertEquals(2, test.requests.size)
        val episodes = test.requests.single { it.path() == EPISODES }
        assertEquals("42", episodes.param("id"))
        assertEquals("100", episodes.param("max"))
        assertNull(episodes.param("since"))
        val podcast = test.requests.single { it.path() == PODCAST }
        assertEquals("42", podcast.param("id"))
        assertEquals("true", podcast.param("pretty"))
    }

    @Test
    fun e10AndE11AreRequestedInParallel() = runTest {
        val e11Arrived = CompletableDeferred<Unit>()
        val test = TestGraph(handler = { request ->
            if (request.path() == EPISODES) {
                // E10 only answers once E11 is in flight: a sequential load would hang here.
                e11Arrived.await()
                respondJson(episodesBody)
            } else {
                e11Arrived.complete(Unit)
                respondJson(cleanFeedBody)
            }
        })

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertEquals(listOf(1L, 3L), detail.episodes.map { it.id })
        assertEquals(1, test.e10())
        assertEquals(1, test.e11())
    }

    @Test
    fun bothSucceedGivesEpisodesAndFeed() = runTest {
        val test = graph()
        test.allowExplicit()

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertEquals(listOf(1L, 2L, 3L), detail.episodes.map { it.id })
        assertEquals("Show", assertNotNull(detail.feed).title)
        assertEquals(0, detail.hiddenExplicitCount)
        assertFalse(detail.isExplicitFeedHidden)
    }

    @Test
    fun e11FailingDoesNotFailTheResult() = runTest {
        val test = graph(podcast = null)
        test.allowExplicit()

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertNull(detail.feed)
        assertEquals(listOf(1L, 2L, 3L), detail.episodes.map { it.id })
    }

    @Test
    fun e10FailingIsAnErrorWhateverE11Says() = runTest {
        val test = graph(episodes = null)

        val result = test.graph.podcastDetailFeed.load(42)

        val error = assertIs<TSDataState.Error<PodcastDetail>>(result)
        assertEquals(500, assertIs<PodcastIndexHttpException>(error.exception).code)
    }

    @Test
    fun e10RetriesThreeTimesThenFails() = runTest {
        val test = graph(episodes = null)

        test.graph.podcastDetailFeed.load(42)

        assertEquals(4, test.e10())
        assertEquals(1, test.e11())
    }

    @Test
    fun e11BodyThatDoesNotParseIsRequestedOnce() = runTest {
        val test = graph(podcast = "not json")
        test.allowExplicit()

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertEquals(1, test.e11())
        assertEquals(1, test.e10())
        assertNull(detail.feed)
        assertEquals(3, detail.episodes.size)
    }

    @Test
    fun successIsCachedFor15MinutesPerFeedId() = runTest {
        val test = graph()
        val feed = test.graph.podcastDetailFeed

        feed.load(42)
        assertEquals(2, test.requests.size)

        test.advance(14.minutes + 59.seconds)
        assertIs<TSDataState.Success<PodcastDetail>>(feed.load(42))
        assertEquals(2, test.requests.size, "+14:59: both answers come from the cache")

        test.advance(2.seconds) // +15:01
        assertIs<TSDataState.Success<PodcastDetail>>(feed.load(42))
        assertEquals(2, test.e10())
        assertEquals(2, test.e11())
    }

    @Test
    fun anotherFeedIdDoesNotUseTheCache() = runTest {
        val test = graph()
        val feed = test.graph.podcastDetailFeed

        feed.load(42)
        feed.load(43)

        assertEquals(4, test.requests.size)
        assertEquals(listOf("42", "43"), test.requests.filter { it.path() == EPISODES }.map { it.param("id") })
        assertEquals(listOf("42", "43"), test.requests.filter { it.path() == PODCAST }.map { it.param("id") })
    }

    @Test
    fun errorsAreNotCached() = runTest {
        var failing = true
        val test = TestGraph(handler = { request ->
            when {
                request.path() == PODCAST -> respondJson(cleanFeedBody)
                failing -> respondJson(serverError, HttpStatusCode.InternalServerError)
                else -> respondJson(episodesBody)
            }
        })
        val feed = test.graph.podcastDetailFeed

        assertIs<TSDataState.Error<PodcastDetail>>(feed.load(42))
        assertEquals(4, test.e10())

        failing = false
        assertIs<TSDataState.Success<PodcastDetail>>(feed.load(42))
        assertEquals(5, test.e10(), "a new request, not a cached error")
        assertEquals(1, test.e11(), "E11 succeeded the first time and stays cached")
    }

    // D6

    @Test
    fun explicitEpisodesAreDroppedByDefault() = runTest {
        val test = graph()

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertEquals(listOf(1L, 3L), detail.episodes.map { it.id })
        assertEquals(1, detail.hiddenExplicitCount)
        assertFalse(detail.isExplicitFeedHidden)
    }

    @Test
    fun explicitFeedHidesEveryEpisodeByDefault() = runTest {
        val test = graph(podcast = explicitFeedBody)

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertTrue(detail.episodes.isEmpty())
        assertTrue(detail.isExplicitFeedHidden)
        assertEquals(3, detail.hiddenExplicitCount)
        assertEquals(true, detail.feed?.explicit)
    }

    @Test
    fun e11FailingNeverHidesTheFeed() = runTest {
        val test = graph(podcast = null)

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertFalse(detail.isExplicitFeedHidden)
        assertEquals(listOf(1L, 3L), detail.episodes.map { it.id }, "explicit episodes still dropped")
    }

    @Test
    fun allowExplicitContentKeepsEverything() = runTest {
        val test = graph(podcast = explicitFeedBody)
        test.allowExplicit()

        val detail = test.graph.podcastDetailFeed.load(42).data()

        assertEquals(listOf(1L, 2L, 3L), detail.episodes.map { it.id })
        assertEquals(0, detail.hiddenExplicitCount)
        assertFalse(detail.isExplicitFeedHidden)
    }

    private companion object {
        const val EPISODES = "episodes/byfeedid"
        const val PODCAST = "podcasts/byfeedid"
    }
}
