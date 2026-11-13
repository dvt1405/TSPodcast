package tss.t.shared.home

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.config.ConfigAPI
import tss.t.shared.fixtures.Fixtures
import tss.t.shared.model.LiveEpisode
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M1-AC6: `MainViewModel.getTrending`'s endpoints, parameters and fallback, plus D6. */
class HomeFeedTest {

    private val trendingBody = """{"count":2,"feeds":[{"id":1,"title":"Trending A"},{"id":2,"title":"Trending B"}]}"""
    private val forYouBody = """{"count":1,"feeds":[{"id":9,"title":"For you"}]}"""

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")
    private fun HttpRequestData.param(name: String) = url.parameters[name]

    private fun TestGraph.only(path: String): HttpRequestData = requests.single { it.path() == path }

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    private fun ids(state: TSDataState<List<Podcast>>) = state.data().map { it.id }

    @Test
    fun trendingSectionsUseMainViewModelParameters() = runTest {
        val test = TestGraph(handler = { respondJson(if (it.param("cat") == null) trendingBody else forYouBody) })

        test.graph.homeFeed.trendingSections("1,2,3")

        val calls = test.requests.filter { it.path() == "podcasts/trending" }
        assertEquals(2, calls.size)
        val trending = calls.single { it.param("cat") == null }
        assertEquals("100", trending.param("max"))
        assertEquals("vi", trending.param("lang"))
        assertEquals("1699395200", trending.param("since"))
        assertNull(trending.param("notcat"))
        val forYou = calls.single { it.param("cat") != null }
        assertEquals("1,2,3", forYou.param("cat"))
        assertEquals("100", forYou.param("max"))
        assertEquals("vi", forYou.param("lang"))
        assertEquals("1699136000", forYou.param("since"))
    }

    @Test
    fun trendingAndForYouAreRequestedInParallel() = runTest {
        val bothArrived = CompletableDeferred<Unit>()
        val lock = Mutex()
        var arrived = 0
        val test = TestGraph(handler = { request ->
            lock.withLock { if (++arrived == 2) bothArrived.complete(Unit) }
            // The first request only answers once the second one is in flight.
            bothArrived.await()
            respondJson(if (request.param("cat") == null) trendingBody else forYouBody)
        })

        val sections = test.graph.homeFeed.trendingSections("1,2,3")

        assertEquals(listOf(1L, 2L), ids(sections.trending))
        assertEquals(listOf(9L), ids(sections.forYou))
    }

    @Test
    fun recentMusicAndLiveParameters() = runTest {
        val test = TestGraph()
        val feed = test.graph.homeFeed

        feed.recent("1,2,3")
        feed.music()
        feed.live()

        val recent = test.only("recent/feeds")
        assertEquals("100", recent.param("max"))
        assertEquals("vi", recent.param("lang"))
        assertEquals("1,2,3", recent.param("cat"))
        assertNull(recent.param("notcat"))
        val music = test.only("podcasts/bymedium")
        assertEquals("music", music.param("medium"))
        assertEquals("40", music.param("max"))
        val live = test.only("episodes/live")
        assertEquals("100", live.param("max"))
    }

    private suspend fun sections(trendingOk: Boolean, forYouOk: Boolean): TrendingSections {
        val test = TestGraph(handler = { request ->
            val isTrending = request.param("cat") == null
            val ok = if (isTrending) trendingOk else forYouOk
            if (ok) respondJson(if (isTrending) trendingBody else forYouBody)
            else respondJson("{}", HttpStatusCode.InternalServerError)
        })
        return test.graph.homeFeed.trendingSections("1,2,3")
    }

    @Test
    fun bothSucceedEachSectionKeepsItsList() = runTest {
        val result = sections(trendingOk = true, forYouOk = true)
        assertEquals(listOf(1L, 2L), ids(result.trending))
        assertEquals(listOf(9L), ids(result.forYou))
    }

    @Test
    fun forYouFailsAndUsesTheTrendingList() = runTest {
        val result = sections(trendingOk = true, forYouOk = false)
        assertEquals(listOf(1L, 2L), ids(result.forYou))
        assertEquals(result.trending, result.forYou)
    }

    @Test
    fun trendingFailsAndUsesTheForYouList() = runTest {
        val result = sections(trendingOk = false, forYouOk = true)
        assertEquals(listOf(9L), ids(result.trending))
        assertEquals(result.forYou, result.trending)
    }

    @Test
    fun bothFailBothAreErrors() = runTest {
        val result = sections(trendingOk = false, forYouOk = false)
        assertIs<TSDataState.Error<*>>(result.trending)
        assertIs<TSDataState.Error<*>>(result.forYou)
    }

    // D6: explicit items, after the blacklist. Ids/feed ids ending in 2 are blacklisted.
    private val liveBody = """{"count":3,"items":[
        {"id":1,"feedId":10,"title":"Clean live","explicit":0},
        {"id":2,"feedId":11,"title":"Explicit live","explicit":1},
        {"id":3,"feedId":12,"title":"Blocked live","explicit":0}]}"""
    private val musicBody = """{"count":3,"feeds":[
        {"id":20,"title":"Clean music","explicit":false},
        {"id":21,"title":"Explicit music","explicit":true},
        {"id":22,"title":"Blocked music","explicit":false}]}"""
    private val noExplicitFieldBody = """{"count":3,"feeds":[
        {"id":30,"title":"No field A"},
        {"id":31,"title":"No field B"},
        {"id":32,"title":"Blocked feed"}]}"""
    private val blacklist: ConfigAPI = FakeConfig.blacklist("""{"feedId":["12","22","32"]}""")

    private suspend fun explicitGraph(allowExplicit: Boolean): TestGraph {
        val test = TestGraph(config = blacklist, handler = { request ->
            respondJson(
                when (request.path()) {
                    "episodes/live" -> liveBody
                    "podcasts/bymedium" -> musicBody
                    else -> noExplicitFieldBody
                },
            )
        })
        if (allowExplicit) test.graph.appPreferences.saveAllowExplicitContent(true)
        return test
    }

    private fun liveIds(state: TSDataState<List<LiveEpisode>>) = state.data().map { it.feedId }

    @Test
    fun explicitItemsAreDroppedByDefault() = runTest {
        val feed = explicitGraph(allowExplicit = false).graph.homeFeed

        assertEquals(listOf(10L), liveIds(feed.live()))
        assertEquals(listOf(20L), ids(feed.music()))
        val sections = feed.trendingSections("1,2,3")
        assertEquals(listOf(30L, 31L), ids(sections.trending), "no explicit field: kept")
        assertEquals(listOf(30L, 31L), ids(sections.forYou))
        assertEquals(listOf(30L, 31L), ids(feed.recent("1,2,3")))
    }

    @Test
    fun allowExplicitContentKeepsEverythingButTheBlacklist() = runTest {
        val feed = explicitGraph(allowExplicit = true).graph.homeFeed

        assertEquals(listOf(10L, 11L), liveIds(feed.live()))
        assertEquals(listOf(20L, 21L), ids(feed.music()))
        assertEquals(listOf(30L, 31L), ids(feed.trendingSections(null).trending))
        assertEquals(listOf(30L, 31L), ids(feed.recent("1,2,3")))
    }

    @Test
    fun realTrendingPayloadHasNoExplicitFieldSoNothingIsDropped() = runTest {
        val test = TestGraph(handler = { respondJson(Fixtures.trending) })

        val sections = test.graph.homeFeed.trendingSections("1,2,3")

        val blacklistOnly = test.graph.getTrendingPodcasts(max = 100, since = 1L)
        val expected = assertIs<TSDataState.Success<TrendingPodcastRes>>(blacklistOnly).data.items.map { it.id }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, ids(sections.trending))
    }

    @Test
    fun neverCallsRecentEpisodesOrNewFeeds() = runTest {
        val test = TestGraph()
        val feed = test.graph.homeFeed

        feed.trendingSections("1,2,3")
        feed.music()
        feed.live()
        feed.recent("1,2,3")

        assertEquals(5, test.requests.size)
        assertTrue(test.requests.none { it.path() == "recent/episodes" || it.path() == "recent/newfeeds" })
    }

    @Test
    fun errorsPassThrough() = runTest {
        val test = TestGraph(handler = { respondJson("{}", HttpStatusCode.InternalServerError) })
        val feed = test.graph.homeFeed

        assertIs<TSDataState.Error<*>>(feed.live())
        assertIs<TSDataState.Error<*>>(feed.music())
        assertIs<TSDataState.Error<*>>(feed.recent("1,2,3"))
    }

    // M6-AC10 / AC11: blocked hosts and hidden shows never reach Home.

    private val spamTrendingBody = """{"count":3,"feeds":[
        {"id":1,"title":"Clean","url":"https://ok.example/1"},
        {"id":2,"title":"Spam","url":"https://ngocbaomedia.com/2"},
        {"id":3,"title":"Spam audio","url":"https://ok.example/3","enclosureUrl":"https://a.ngocbaomedia.com/3.mp3"}]}"""
    private val spamMusicBody = """{"count":2,"feeds":[
        {"id":20,"title":"Clean music","url":"https://ok.example/20"},
        {"id":21,"title":"Spam music","url":"https://www.ngocbaomedia.com/21"}]}"""
    private val spamLiveBody = """{"count":2,"items":[
        {"id":1,"feedId":10,"title":"Clean live","enclosureUrl":"https://ok.example/l.mp3"},
        {"id":2,"feedId":11,"title":"Spam live","enclosureUrl":"https://ngocbaomedia.com/l.mp3"}]}"""

    private fun spamGraph() = TestGraph(handler = { request ->
        respondJson(
            when (request.path()) {
                "episodes/live" -> spamLiveBody
                "podcasts/bymedium" -> spamMusicBody
                else -> spamTrendingBody
            },
        )
    })

    @Test
    fun blockedHostsNeverReachAnySection() = runTest {
        val feed = spamGraph().graph.homeFeed

        val sections = feed.trendingSections("1,2,3")
        assertEquals(listOf(1L), ids(sections.trending))
        assertEquals(listOf(1L), ids(sections.forYou))
        assertEquals(listOf(1L), ids(feed.recent("1,2,3")))
        assertEquals(listOf(20L), ids(feed.music()))
        assertEquals(listOf(10L), liveIds(feed.live()))
    }

    @Test
    fun hiddenShowsAreDroppedFromEverySection() = runTest {
        val test = TestGraph(handler = { request ->
            respondJson(
                when (request.path()) {
                    "episodes/live" -> liveBody
                    "podcasts/bymedium" -> musicBody
                    else -> trendingBody
                },
            )
        })
        val hidden = test.graph.hiddenShows
        hidden.hide(feedId = 1, title = "Trending A", imageUrl = null)
        hidden.hide(feedId = 20, title = "Clean music", imageUrl = null)
        hidden.hide(feedId = 10, title = "Live feed", imageUrl = null)
        val feed = test.graph.homeFeed

        val sections = feed.trendingSections("1,2,3")
        assertEquals(listOf(2L), ids(sections.trending))
        assertEquals(listOf(2L), ids(sections.forYou))
        assertEquals(listOf(2L), ids(feed.recent("1,2,3")))
        assertEquals(listOf(22L), ids(feed.music()), "21 is explicit (D6), no blacklist here")
        assertEquals(listOf(12L), liveIds(feed.live()), "11 is explicit (D6)")

        // Relaunch on the same store: still hidden.
        val relaunch = TestGraph(store = test.store, handler = { respondJson(trendingBody) })
        assertEquals(listOf(2L), ids(relaunch.graph.homeFeed.recent("1,2,3")))
    }
}
