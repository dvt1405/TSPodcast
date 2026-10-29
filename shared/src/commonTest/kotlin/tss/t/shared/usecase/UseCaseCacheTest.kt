package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UseCaseCacheTest {

    private val trending = """{"count":1,"feeds":[{"id":5,"title":"Clean"}]}"""
    private val episodes = """{"count":1,"items":[{"id":1,"feedId":42,"title":"Ep"}]}"""
    private val podcast = """{"feed":{"id":42,"title":"Show"},"query":{"id":"42"}}"""
    private val categories = """{"count":1,"feeds":[{"id":1,"name":"Arts"}]}"""

    private fun graph(status: () -> HttpStatusCode = { HttpStatusCode.OK }) = TestGraph(handler = { request ->
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("podcasts/trending") -> trending
            path.endsWith("episodes/byfeedid") -> episodes
            path.endsWith("podcasts/byfeedid") -> podcast
            path.endsWith("categories/list") -> categories
            else -> "{}"
        }
        respondJson(body, status())
    })

    @Test
    fun trendingIsCachedForFifteenMinutesWhateverTheSince() = runTest {
        val test = graph()
        val trending = test.graph.getTrendingPodcasts

        assertIs<TSDataState.Success<*>>(trending(max = 100, since = 1_000L))
        test.advance(14.minutes + 59.seconds)
        assertIs<TSDataState.Success<*>>(trending(max = 100, since = 2_000L))
        assertEquals(1, test.requestsTo("podcasts/trending"))

        test.advance(2.seconds) // 15:01 after the first request
        trending(max = 100, since = 3_000L)
        assertEquals(2, test.requestsTo("podcasts/trending"))
    }

    @Test
    fun trendingIsKeyedByNormalizedCategory() = runTest {
        val test = graph()
        val trending = test.graph.getTrendingPodcasts

        trending(max = 100, since = 1L, cat = "1")
        trending(max = 100, since = 1L, cat = "2")
        trending(max = 100, since = 1L, cat = "1")
        assertEquals(2, test.requestsTo("podcasts/trending"))

        trending(max = 100, since = 1L, cat = null)
        trending(max = 100, since = 1L, cat = "")
        trending(max = 100, since = 1L, cat = "  ")
        assertEquals(3, test.requestsTo("podcasts/trending"))
    }

    @Test
    fun episodesByFeedAreCachedPerId() = runTest {
        val test = graph()
        val episodesByFeed = test.graph.getEpisodeByFeedId

        episodesByFeed("42")
        episodesByFeed("42")
        assertEquals(1, test.requestsTo("episodes/byfeedid"))

        episodesByFeed("43")
        assertEquals(2, test.requestsTo("episodes/byfeedid"))

        test.advance(15.minutes + 1.seconds)
        episodesByFeed("42")
        assertEquals(3, test.requestsTo("episodes/byfeedid"))
    }

    @Test
    fun podcastByFeedIsCachedPerId() = runTest {
        val test = graph()
        val podcastByFeed = test.graph.getPodcastByFeedID

        podcastByFeed("42")
        test.advance(14.minutes + 59.seconds)
        podcastByFeed("42")
        assertEquals(1, test.requestsTo("podcasts/byfeedid"))

        podcastByFeed("43")
        assertEquals(2, test.requestsTo("podcasts/byfeedid"))

        test.advance(2.seconds)
        podcastByFeed("42")
        assertEquals(3, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun errorsAreNotCached() = runTest {
        var status = HttpStatusCode.InternalServerError
        val test = graph { status }

        assertIs<TSDataState.Error<*>>(test.graph.getTrendingPodcasts(max = 100, since = 1L))
        assertEquals(1, test.requestsTo("podcasts/trending"))
        assertIs<TSDataState.Error<*>>(test.graph.getEpisodeByFeedId("42"))
        assertEquals(4, test.requestsTo("episodes/byfeedid"))
        assertIs<TSDataState.Error<*>>(test.graph.getPodcastByFeedID("42"))
        assertEquals(4, test.requestsTo("podcasts/byfeedid"))

        status = HttpStatusCode.OK
        assertIs<TSDataState.Success<*>>(test.graph.getTrendingPodcasts(max = 100, since = 1L))
        assertIs<TSDataState.Success<*>>(test.graph.getEpisodeByFeedId("42"))
        assertIs<TSDataState.Success<*>>(test.graph.getPodcastByFeedID("42"))
        assertEquals(2, test.requestsTo("podcasts/trending"))
        assertEquals(5, test.requestsTo("episodes/byfeedid"))
        assertEquals(5, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun categoriesFromMemoryWhenUseCache() = runTest {
        val test = graph()
        val getCategories = test.graph.getCategories

        assertIs<TSDataState.Success<CategoryRes>>(getCategories(useCache = true))
        assertEquals(1, test.requestsTo("categories/list"), "nothing in memory yet: one request")

        val cached = assertIs<TSDataState.Success<CategoryRes>>(getCategories(useCache = true))
        assertEquals(1, test.requestsTo("categories/list"), "useCache = true after a success: no request")
        assertEquals(listOf("Arts"), cached.data.feeds.map { it.name })

        getCategories(useCache = false)
        assertEquals(2, test.requestsTo("categories/list"), "useCache = false always asks the network")
    }

    @Test
    fun cachedTrendingIsTheFilteredResult() = runTest {
        val test = TestGraph(
            config = FakeConfig.blacklist("""{"feedId":["5"]}"""),
            handler = { respondJson(trending) },
        )

        val first = test.graph.getTrendingPodcasts(max = 100, since = 1L)
        val second = test.graph.getTrendingPodcasts(max = 100, since = 2L)

        assertEquals(0, assertIs<TSDataState.Success<TrendingPodcastRes>>(first).data.count)
        assertEquals(first, second)
        assertEquals(1, test.requestsTo("podcasts/trending"))
    }
}
