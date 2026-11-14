package tss.t.shared.location

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.network.respondJson
import tss.t.shared.usecase.GetLocationNews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Android `GetPodcastsByLocation` / `GetLocationNews` and the title filter of trending and recent,
 * through the real graph (`HomeFeed.byLocation`, `HomeFeed.locationNews`, `trendingSections`).
 */
class LocationFeedTest {

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")
    private fun HttpRequestData.param(name: String) = url.parameters[name]
    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    // 2 is blacklisted, 3 is Chinese spam claiming Vietnamese, 4 repeats 1, 5 is explicit.
    private val trendingBody = """{"count":5,"feeds":[
        {"id":1,"title":"Tin sáng"},
        {"id":2,"title":"Blocked"},
        {"id":3,"title":"公众号均速阅读模拟提升指南推荐"},
        {"id":1,"title":"Tin sáng"},
        {"id":5,"title":"Người lớn","explicit":1}]}"""
    private val blacklist = FakeConfig.blacklist("""{"feedId":["2"]}""")

    private val vietnam = PodcastLocations.byRegion("VN")!!
    private val japan = PodcastLocations.byRegion("JP")!!

    @Test
    fun byLocationRequestsTheLocationLanguage() = runTest {
        val test = TestGraph(config = blacklist, handler = { respondJson(trendingBody) })

        val podcasts = test.graph.homeFeed.byLocation(vietnam, force = false).data()

        val request = test.requests.single()
        assertEquals("podcasts/trending", request.path())
        assertEquals("vi", request.param("lang"))
        assertEquals("40", request.param("max"))
        assertEquals("1699395200", request.param("since"), "now − 7 days")
        assertEquals(null, request.param("cat"))
        assertEquals(listOf(1L), podcasts.map { it.id }, "blacklist, title filter, one per id, D6")
    }

    @Test
    fun byLocationKeepsCjkTitlesForCjkLocations() = runTest {
        val test = TestGraph(handler = { respondJson(trendingBody) })

        val podcasts = test.graph.homeFeed.byLocation(japan, force = false).data()

        assertEquals("ja", test.requests.single().param("lang"))
        assertEquals(listOf(1L, 2L, 3L), podcasts.map { it.id })
    }

    @Test
    fun byLocationAllowsExplicitAndDropsHiddenShows() = runTest {
        val test = TestGraph(config = blacklist, handler = { respondJson(trendingBody) })
        test.graph.appPreferences.saveAllowExplicitContent(true)
        test.graph.hiddenShows.hide(feedId = 1, title = "Tin sáng", imageUrl = null)

        assertEquals(listOf(5L), test.graph.homeFeed.byLocation(vietnam, force = false).data().map { it.id })
    }

    @Test
    fun byLocationIsCachedPerLocationUnlessForced() = runTest {
        val test = TestGraph(handler = { respondJson(trendingBody) })
        val feed = test.graph.homeFeed

        feed.byLocation(vietnam, force = false)
        feed.byLocation(japan, force = false)
        test.advance(14.minutes)
        feed.byLocation(vietnam, force = false)
        feed.byLocation(japan, force = false)
        assertEquals(listOf("vi", "ja"), test.requests.map { it.param("lang") }, "chips do not refetch")

        feed.byLocation(vietnam, force = true)
        assertEquals(3, test.requests.size, "pull-to-refresh skips the cache")
        test.advance(2.minutes)
        feed.byLocation(japan, force = false)
        assertEquals(4, test.requests.size, "+16 min: expired")
    }

    @Test
    fun byLocationErrorIsAnError() = runTest {
        val test = TestGraph(handler = { respondJson("""{"status":"false"}""", HttpStatusCode.InternalServerError) })

        assertIs<TSDataState.Error<*>>(test.graph.homeFeed.byLocation(vietnam, force = false))
        assertIs<TSDataState.Error<*>>(test.graph.homeFeed.byLocation(vietnam, force = false))
        assertEquals(2, test.requests.size, "errors are not cached")
    }

    // "Thời sự" from the catalog.

    private val nowSeconds = TestGraph.START_MILLIS / 1_000
    private val newsShowsBody = """{"count":3,"feeds":[
        {"id":10,"title":"Thời sự 24h","image":"https://img/10.jpg"},
        {"id":11,"title":"Bản tin tối"},
        {"id":12,"title":"Show im lặng"}]}"""
    private val newsEpisodesBody = """{"count":5,"items":[
        {"id":100,"feedId":10,"title":"Old 10","datePublished":${nowSeconds - 3_600 * 5},"enclosureUrl":"https://a/100.mp3"},
        {"id":101,"feedId":10,"title":"New 10","datePublished":${nowSeconds - 3_600},"enclosureUrl":"https://a/101.mp3"},
        {"id":110,"feedId":11,"title":"New 11","datePublished":${nowSeconds - 60},"enclosureUrl":"https://a/110.mp3","explicit":1},
        {"id":111,"feedId":11,"title":"No audio","datePublished":${nowSeconds},"enclosureUrl":""},
        {"id":120,"feedId":12,"title":"Too old","datePublished":${nowSeconds - 3_600 * 80},"enclosureUrl":"https://a/120.mp3"},
        {"id":130,"feedId":13,"title":"Not a trending show","datePublished":${nowSeconds},"enclosureUrl":"https://a/130.mp3"}]}"""

    private fun newsGraph() = TestGraph(handler = { request ->
        respondJson(if (request.path() == "podcasts/trending") newsShowsBody else newsEpisodesBody)
    })

    @Test
    fun locationNewsIsTheNewestEpisodeOfEachTrendingNewsShow() = runTest {
        val test = newsGraph()
        test.graph.appPreferences.saveAllowExplicitContent(true)

        val items = test.graph.homeFeed.locationNews(vietnam, force = false)

        val shows = test.requests.single { it.path() == "podcasts/trending" }
        assertEquals(GetLocationNews.NEWS_CATEGORY, shows.param("cat"))
        assertEquals("vi", shows.param("lang"))
        assertEquals("40", shows.param("max"))
        assertEquals((nowSeconds - 10 * 86_400).toString(), shows.param("since"))
        val episodes = test.requests.single { it.path() == "episodes/byfeedid" }
        assertEquals("10,11,12", episodes.param("id"))
        assertEquals("100", episodes.param("max"))
        assertEquals((nowSeconds - 72 * 3_600).toString(), episodes.param("since"))

        assertEquals(listOf(110L, 101L), items.map { it.episode.id }, "newest first, one per show, with audio, in 72 h")
        assertEquals(listOf("catalog:11", "catalog:10"), items.map { it.sourceKey })
        assertEquals("Thời sự 24h", items[1].feed.title)
        assertEquals("https://img/10.jpg", items[1].feed.artwork)
    }

    @Test
    fun locationNewsDropsExplicitByDefaultAndHiddenShows() = runTest {
        val test = newsGraph()

        assertEquals(listOf(101L), test.graph.homeFeed.locationNews(vietnam, force = false).map { it.episode.id }, "D6")

        test.graph.appPreferences.saveAllowExplicitContent(true)
        test.graph.hiddenShows.hide(feedId = 10, title = "Thời sự 24h", imageUrl = null)
        assertEquals(listOf(110L), test.graph.homeFeed.locationNews(vietnam, force = false).map { it.episode.id })
    }

    @Test
    fun locationNewsIsEmptyWithoutShowsOrOnErrors() = runTest {
        val noShows = TestGraph(handler = { respondJson("""{"count":0,"feeds":[]}""") })
        assertTrue(noShows.graph.homeFeed.locationNews(vietnam, force = false).isEmpty())
        assertEquals(1, noShows.requests.size, "no episode request without shows")

        val failing = TestGraph(handler = { request ->
            if (request.path() == "podcasts/trending") respondJson(newsShowsBody)
            else respondJson("""{"status":"false"}""", HttpStatusCode.InternalServerError)
        })
        assertTrue(failing.graph.homeFeed.locationNews(vietnam, force = false).isEmpty())
    }

    // Home lists in the primary location's language, without spam titled in another script.

    @Test
    fun homeListsUseThePrimaryLanguageAndDropOtherScripts() = runTest {
        val test = TestGraph(handler = { respondJson(trendingBody) })
        test.graph.podcastLocations.setLocations(listOf("US", "VN"))

        val sections = test.graph.homeFeed.trendingSections(null)
        val recent = test.graph.homeFeed.recent(null).data()

        assertTrue(test.requests.all { it.param("lang") == "en" }, "one language, no ',en'")
        assertEquals(listOf(1L, 2L, 1L), sections.trending.data().map { it.id }, "Chinese title dropped, D6")
        assertEquals(listOf(1L, 2L, 1L), recent.map { it.id })
    }

    @Test
    fun emptyForYouFallsBackToTrending() = runTest {
        val test = TestGraph(handler = { request ->
            respondJson(if (request.param("cat") == null) trendingBody else """{"count":0,"feeds":[]}""")
        })

        val sections = test.graph.homeFeed.trendingSections("9")

        assertEquals(sections.trending, sections.forYou)
        assertEquals(listOf(1L, 2L, 1L), sections.forYou.data().map { it.id })
    }
}
