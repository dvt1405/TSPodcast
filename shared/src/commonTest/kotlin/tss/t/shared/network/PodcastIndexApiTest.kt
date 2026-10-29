package tss.t.shared.network

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PodcastIndexApiTest {

    private val credentials = FakeCredentials(baseUrl = "https://api.example.test/api/1.0/")
    private val mock = MockApi(credentials = credentials)
    private val api = mock.api

    private fun single(): HttpRequestData {
        assertEquals(1, mock.requests.size, "expected exactly one request")
        return mock.requests.single()
    }

    private fun assertRequest(
        path: String,
        query: Map<String, String>,
        request: HttpRequestData = single(),
    ) {
        assertEquals("https", request.url.protocol.name)
        assertEquals("api.example.test", request.url.host)
        assertEquals("/api/1.0/$path", request.url.encodedPath)
        val actual = request.url.parameters.names().associateWith { request.url.parameters[it]!! }
        assertEquals(query, actual)
        assertSigned(request)
    }

    private fun assertSigned(request: HttpRequestData) {
        val date = request.headers["X-Auth-Date"]
        assertEquals("1700000000", date)
        assertEquals("testkey", request.headers["X-Auth-Key"])
        assertEquals("TSPodcastTest/1.0", request.headers["User-Agent"])
        assertEquals(
            PodcastIndexAuth.authorization("testkey", "testsecret", date!!),
            request.headers["Authorization"],
        )
    }

    @Test
    fun e1TrendingWithoutCategory() = runTest {
        api.getTrending(since = 1_699_395_200L)

        assertRequest(
            "podcasts/trending",
            mapOf("max" to "100", "since" to "1699395200", "lang" to "vi"),
        )
    }

    @Test
    fun e1TrendingWithCategory() = runTest {
        api.getTrending(since = 1_699_395_200L, cat = "1,2")

        assertRequest(
            "podcasts/trending",
            mapOf("max" to "100", "since" to "1699395200", "lang" to "vi", "cat" to "1,2"),
        )
    }

    @Test
    fun e1BlankCategoryIsNotSent() = runTest {
        api.getTrending(since = 1L, cat = "", notcat = " ")

        assertRequest("podcasts/trending", mapOf("max" to "100", "since" to "1", "lang" to "vi"))
    }

    @Test
    fun e2LiveEpisodes() = runTest {
        api.getLiveEpisodes()

        assertRequest("episodes/live", mapOf("max" to "100"))
    }

    @Test
    fun e5RecentFeeds() = runTest {
        api.getRecentFeeds(cat = "9,55")

        assertRequest("recent/feeds", mapOf("max" to "100", "lang" to "vi", "cat" to "9,55"))
    }

    @Test
    fun e6PodcastsByMedium() = runTest {
        api.getPodcastsByMedium()

        assertRequest("podcasts/bymedium", mapOf("medium" to "music", "max" to "40"))
    }

    @Test
    fun e7SearchByTermWithoutClean() = runTest {
        api.searchPodcasts("tin tức")

        assertRequest(
            "search/byterm",
            mapOf(
                "q" to "tin tức",
                "max" to "100",
                "similar" to "true",
                "fulltext" to "true",
                "aponly" to "false",
            ),
        )
    }

    @Test
    fun e7SearchByTermWithClean() = runTest {
        api.searchPodcasts("news", clean = true)

        assertRequest(
            "search/byterm",
            mapOf(
                "q" to "news",
                "max" to "100",
                "similar" to "true",
                "fulltext" to "true",
                "aponly" to "false",
                "clean" to "true",
            ),
        )
    }

    @Test
    fun e8SearchMusic() = runTest {
        api.searchMusicPodcasts("rock")

        assertRequest(
            "search/music/byterm",
            mapOf(
                "q" to "rock",
                "val" to "music",
                "max" to "60",
                "similar" to "true",
                "fulltext" to "true",
            ),
        )
    }

    @Test
    fun e8BlankQueryReturnsEmptySuccessWithoutRequest() = runTest {
        val result = api.searchMusicPodcasts("  ")

        val data = assertIs<TSDataState.Success<*>>(result).data as SearchResponse
        assertEquals(0, data.count)
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun e9Categories() = runTest {
        api.getCategories()

        assertRequest("categories/list", emptyMap())
    }

    @Test
    fun e10EpisodesByFeedId() = runTest {
        api.getEpisodesByFeedId("75075")

        assertRequest("episodes/byfeedid", mapOf("id" to "75075", "max" to "100"))
    }

    @Test
    fun e11PodcastByFeedId() = runTest {
        api.getPodcastByFeedId("75075")

        assertRequest("podcasts/byfeedid", mapOf("id" to "75075", "pretty" to "true"))
    }

    @Test
    fun baseUrlWithoutTrailingSlashKeepsItsPath() = runTest {
        val noSlash = MockApi(credentials = credentials.copy(baseUrl = "https://api.example.test/api/1.0"))

        noSlash.api.getLiveEpisodes()

        assertRequest("episodes/live", mapOf("max" to "100"), noSlash.requests.single())
    }

    @Test
    fun successBodyIsParsed() = runTest {
        val ok = MockApi { respondJson("""{"status":"true","feeds":[{"id":1,"title":"A"}],"count":1,"max":"100"}""") }

        val result = ok.api.getTrending(since = 1L)

        val data = assertIs<TSDataState.Success<*>>(result).data as TrendingPodcastRes
        assertEquals(1, data.count)
        assertEquals(100, data.max)
        assertEquals("A", data.items.single().title)
        assertNull(data.items.single().feedId)
    }

    @Test
    fun httpErrorMapsToPodcastIndexHttpException() = runTest {
        val failing = MockApi { respondJson("""{"status":"false"}""", HttpStatusCode.Unauthorized) }

        val result = failing.api.getLiveEpisodes()

        val exception = assertIs<PodcastIndexHttpException>(assertIs<TSDataState.Error<*>>(result).exception)
        assertEquals(401, exception.code)
        assertEquals("""{"status":"false"}""", exception.body)
    }

    @Test
    fun unparsableBodyIsAnError() = runTest {
        val broken = MockApi { respondJson("not json") }

        val result = broken.api.getCategories()

        assertIs<TSDataState.Error<*>>(result)
        assertEquals(1, broken.requests.size)
    }
}
