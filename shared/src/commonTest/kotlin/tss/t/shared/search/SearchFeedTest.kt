package tss.t.shared.search

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.config.ConfigAPI
import tss.t.shared.model.TSDataState
import tss.t.shared.network.respondJson
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * M2-AC2 and M2-AC3: `SearchViewModel.performSearch` (E7/E8) with a trimmed query, the
 * blacklist and D6, and trending per category (E1 `cat=<id>`).
 */
class SearchFeedTest {

    // Feed 12 is blacklisted by id, "Casino night" by the keyword in its title, 13 is explicit.
    private val searchBody = """{"count":4,"feeds":[
        {"id":11,"title":"Vietnam stories","explicit":false},
        {"id":12,"title":"Blocked by id","explicit":false},
        {"id":13,"title":"Explicit show","explicit":true},
        {"id":14,"title":"Casino night","explicit":false}]}"""
    private val trendingBody = """{"count":3,"feeds":[
        {"id":21,"title":"Arts A"},
        {"id":12,"title":"Blocked by id"},
        {"id":23,"title":"Arts B"}]}"""
    private val blacklist: ConfigAPI =
        FakeConfig.blacklist("""{"feedId":["12"],"filterText":["casino"]}""")

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")
    private fun HttpRequestData.param(name: String) = url.parameters[name]

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    private fun graph(status: HttpStatusCode = HttpStatusCode.OK) = TestGraph(
        config = blacklist,
        handler = { request ->
            when {
                status != HttpStatusCode.OK -> respondJson("""{"status":"false"}""", status)
                request.path() == TRENDING -> respondJson(trendingBody)
                else -> respondJson(searchBody)
            }
        },
    )

    // M2-AC2: search

    @Test
    fun allUsesSearchByTermWithCleanByDefault() = runTest {
        val test = graph()

        test.graph.searchFeed.search("vietnam", SearchMode.All)

        val request = test.requests.single()
        assertEquals("search/byterm", request.path())
        assertEquals("vietnam", request.param("q"))
        assertEquals("100", request.param("max"))
        assertEquals("true", request.param("similar"))
        assertEquals("true", request.param("fulltext"))
        assertEquals("false", request.param("aponly"))
        assertEquals("true", request.param("clean"))
        assertNull(request.param("val"))
    }

    @Test
    fun musicUsesSearchMusicByTermWithCleanByDefault() = runTest {
        val test = graph()

        test.graph.searchFeed.search("piano", SearchMode.Music)

        val request = test.requests.single()
        assertEquals("search/music/byterm", request.path())
        assertEquals("piano", request.param("q"))
        assertEquals("music", request.param("val"))
        assertEquals("60", request.param("max"))
        assertEquals("true", request.param("similar"))
        assertEquals("true", request.param("fulltext"))
        assertEquals("true", request.param("clean"))
        assertNull(request.param("aponly"))
    }

    @Test
    fun queryIsTrimmed() = runTest {
        val test = graph()

        test.graph.searchFeed.search("  vietnam  ", SearchMode.All)
        test.graph.searchFeed.search("\tpiano \n", SearchMode.Music)

        assertEquals(listOf("vietnam", "piano"), test.requests.map { it.param("q") })
        assertEquals(listOf("search/byterm", "search/music/byterm"), test.requests.map { it.path() })
    }

    @Test
    fun blankQuerySendsNoRequest() = runTest {
        val test = graph()
        val feed = test.graph.searchFeed

        for (mode in SearchMode.entries) {
            for (query in listOf("", "   ")) {
                assertEquals(emptyList(), feed.search(query, mode).data(), "mode=$mode query='$query'")
            }
        }

        assertEquals(0, test.requests.size)
    }

    @Test
    fun blacklistAndExplicitFeedsAreDroppedByDefault() = runTest {
        val test = graph()
        val feed = test.graph.searchFeed

        assertEquals(listOf(11L), feed.search("vietnam", SearchMode.All).data().map { it.id })
        assertEquals(listOf(11L), feed.search("piano", SearchMode.Music).data().map { it.id })
    }

    @Test
    fun allowExplicitContentSendsNoCleanAndKeepsExplicitFeeds() = runTest {
        val test = graph()
        test.graph.appPreferences.saveAllowExplicitContent(true)
        val feed = test.graph.searchFeed

        val all = feed.search("vietnam", SearchMode.All).data()
        val music = feed.search("piano", SearchMode.Music).data()

        assertEquals(listOf(11L, 13L), all.map { it.id }, "blacklist still applies")
        assertEquals(listOf(11L, 13L), music.map { it.id })
        assertEquals(2, test.requests.size)
        assertTrue(test.requests.none { "clean" in it.url.parameters.names() })
        assertEquals("search/byterm", test.requests[0].path())
        assertEquals("100", test.requests[0].param("max"))
        assertEquals("search/music/byterm", test.requests[1].path())
        assertEquals("music", test.requests[1].param("val"))
    }

    @Test
    fun serverErrorIsAnErrorAfterOneRequest() = runTest {
        for (mode in SearchMode.entries) {
            val test = graph(HttpStatusCode.InternalServerError)

            assertIs<TSDataState.Error<*>>(test.graph.searchFeed.search("vietnam", mode))
            assertEquals(1, test.requests.size, "mode=$mode: search does not retry")
        }
    }

    // M2-AC3: trending per category

    @Test
    fun categoryPodcastsCallTrendingWithTheCategory() = runTest {
        val test = graph()
        assertEquals(1_700_000_000L, test.clock.millis / 1_000)

        val podcasts = test.graph.searchFeed.categoryPodcasts(55).data()

        val request = test.requests.single()
        assertEquals(TRENDING, request.path())
        assertEquals("100", request.param("max"))
        assertEquals("1699395200", request.param("since"))
        assertEquals("vi,en", request.param("lang"))
        assertEquals("55", request.param("cat"))
        assertFalse("notcat" in request.url.parameters.names())
        assertEquals(listOf(21L, 23L), podcasts.map { it.id }, "blacklisted item dropped")
    }

    @Test
    fun categoryPodcastsAreCachedInMemoryAndOnDisk() = runTest {
        val store = InMemoryKeyValueStore(mapOf(tss.t.shared.i18n.ContentLanguageRepository.KEY_LANGUAGE to "vi"))
        val test = TestGraph(config = blacklist, store = store, handler = { respondJson(trendingBody) })
        val feed = test.graph.searchFeed

        feed.categoryPodcasts(55)
        test.advance(14.minutes + 59.seconds)
        val again = feed.categoryPodcasts(55).data()

        assertEquals(1, test.requests.size, "+14:59 comes from the cache")
        assertEquals(listOf(21L, 23L), again.map { it.id })
        val keys = store.snapshot().keys
        assertTrue(AppPreferences.cachedTrendingKey("55") in keys)
        assertTrue("cachedTrending_55" in keys)

        // A relaunch on the same store, still inside 15 minutes: served from disk.
        val relaunch = TestGraph(config = blacklist, clock = test.clock, store = store, handler = { respondJson(trendingBody) })
        assertEquals(listOf(21L, 23L), relaunch.graph.searchFeed.categoryPodcasts(55).data().map { it.id })
        assertEquals(0, relaunch.requests.size)

        test.advance(2.seconds) // +15:01
        feed.categoryPodcasts(55)
        assertEquals(2, test.requests.size)
    }

    @Test
    fun eachCategoryHasItsOwnEntry() = runTest {
        val test = graph()
        val feed = test.graph.searchFeed

        feed.categoryPodcasts(55)
        feed.categoryPodcasts(9)

        assertEquals(listOf("55", "9"), test.requests.map { it.param("cat") })
    }

    @Test
    fun categoryPodcastsServerErrorIsAnError() = runTest {
        val test = graph(HttpStatusCode.InternalServerError)

        assertIs<TSDataState.Error<*>>(test.graph.searchFeed.categoryPodcasts(55))
        assertEquals(1, test.requests.size)
        assertFalse("cachedTrending_55" in (test.store as InMemoryKeyValueStore).snapshot().keys)
    }

    // M6-AC10 / AC11: blocked hosts never reach Search, hidden shows are dropped last.

    private val spamSearchBody = """{"count":3,"feeds":[
        {"id":11,"title":"Vietnam stories","url":"https://ok.example/rss","explicit":false},
        {"id":15,"title":"Spam","url":"https://feeds.ngocbaomedia.com/rss","explicit":false},
        {"id":16,"title":"Moved spam","url":"https://ok.example/16","originalUrl":"https://ngocbaomedia.com/16","explicit":false}]}"""
    private val spamTrendingBody = """{"count":3,"feeds":[
        {"id":21,"title":"Arts A","url":"https://ok.example/a"},
        {"id":24,"title":"Spam","url":"https://ngocbaomedia.com/24"},
        {"id":25,"title":"Spam audio","enclosureUrl":"https://www.ngocbaomedia.com/25.mp3"}]}"""

    @Test
    fun blockedHostsNeverReachSearchOrCategories() = runTest {
        val test = TestGraph(handler = { request ->
            respondJson(if (request.path() == TRENDING) spamTrendingBody else spamSearchBody)
        })
        val feed = test.graph.searchFeed

        assertEquals(listOf(11L), feed.search("q", SearchMode.All).data().map { it.id })
        assertEquals(listOf(11L), feed.search("q", SearchMode.Music).data().map { it.id })
        assertEquals(listOf(21L), feed.categoryPodcasts(55).data().map { it.id })
    }

    @Test
    fun hiddenShowsAreDroppedFromSearchAndCategories() = runTest {
        val test = graph()
        test.graph.hiddenShows.hide(feedId = 11, title = "Vietnam stories", imageUrl = null)
        test.graph.hiddenShows.hide(feedId = 23, title = "Arts B", imageUrl = null)
        test.graph.appPreferences.saveAllowExplicitContent(true)
        val feed = test.graph.searchFeed

        assertEquals(listOf(13L), feed.search("vietnam", SearchMode.All).data().map { it.id })
        assertEquals(listOf(21L), feed.categoryPodcasts(55).data().map { it.id })

        test.graph.hiddenShows.unhide(11)
        assertEquals(listOf(11L, 13L), feed.search("vietnam", SearchMode.All).data().map { it.id })
    }

    private companion object {
        const val TRENDING = "podcasts/trending"
    }
}
