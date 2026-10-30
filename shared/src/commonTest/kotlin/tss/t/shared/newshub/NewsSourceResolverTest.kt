package tss.t.shared.newshub

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.config.ConfigKeys
import tss.t.shared.crash.FakeCrashReporter
import tss.t.shared.model.TSDataState
import tss.t.shared.network.FixedClock
import tss.t.shared.network.MockApi
import tss.t.shared.network.respondJson
import tss.t.shared.preferences.InMemoryKeyValueStore
import tss.t.shared.preferences.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * M6-AC15: sources resolve through PodcastIndex only (`byitunesid` on the right host, else
 * `byfeedurl`), are cached 7 days on the device, episodes come from `episodes/byfeedid?max=20`
 * cached 15 minutes, and unresolved sources are dropped and reported once.
 */
class NewsSourceResolverTest {

    private val allowedPaths = setOf("podcasts/byitunesid", "podcasts/byfeedurl", "episodes/byfeedid")

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")

    private val vov = NewsSource(
        key = "vov",
        feedUrl = "https://anchor.example.test/s/86ec8d4/podcast/rss",
        itunesId = 1448565355,
        confirmedAt = "2026-11-02",
        brief = NewsBriefRule(take = 1),
    )
    private val night = NewsSource(key = "night", feedUrl = "https://www.night.example.test/podcast/rss", confirmedAt = "2026-11-02")

    /** `podcasts/by*` body; the API sends `query.id` as a number for `byitunesid`. */
    private fun feedBody(id: Long, url: String, originalUrl: String = url, dead: Int = 0, extra: String = "") =
        """{"status":"true","query":{"id":1448565355},"feed":{"id":$id,"title":"Feed $id","url":"$url",
            "originalUrl":"$originalUrl","dead":$dead,"explicit":false$extra},"description":"Found matching feed."}"""

    private val notFound = """{"status":"false","description":"Feed url not found."}"""

    private class Server(
        var byItunesId: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        var byFeedUrl: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    )

    private inner class Fixture(
        val server: Server,
        val store: KeyValueStore = InMemoryKeyValueStore(),
        val clock: FixedClock = FixedClock(NOW),
        blacklist: String? = null,
    ) {
        val crash = FakeCrashReporter()
        val mock = MockApi(clock = clock) { request ->
            when (request.path()) {
                "podcasts/byitunesid" -> server.byItunesId(this, request)
                "podcasts/byfeedurl" -> server.byFeedUrl(this, request)
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }
        val reported = mutableListOf<String>()
        val resolver = NewsSourceResolver(
            api = mock.api,
            store = store,
            clock = clock,
            blacklist = BlacklistRepository(FakeConfig.blacklist(blacklist)),
            crash = crash,
        ).also { it.unresolvedListener = NewsSourceUnresolvedListener { key -> reported += key } }

        fun paths() = mock.requests.map { it.path() }

        fun advance(by: Duration) {
            clock.millis += by.inWholeMilliseconds
        }
    }

    private fun matchingServer() = Server(
        byItunesId = { respondJson(feedBody(101, "https://anchor.example.test/s/86ec8d4/podcast/rss")) },
        byFeedUrl = { respondJson(feedBody(202, "https://anchor.example.test/s/86ec8d4/podcast/rss")) },
    )

    private fun NewsSourceResolution.resolved() = assertIs<NewsSourceResolution.Resolved>(this)

    @Test
    fun itunesIdOnTheSameHostNeedsOneRequest() = runTest {
        val fixture = Fixture(matchingServer())

        val result = fixture.resolver.resolve(vov).resolved()

        assertEquals(101, result.feed.id)
        assertEquals(NewsSourcePath.ITUNES_ID, result.path)
        assertEquals(listOf("podcasts/byitunesid"), fixture.paths())
        assertEquals("1448565355", fixture.mock.requests.single().url.parameters["id"])
    }

    @Test
    fun originalUrlOnTheHostIsEnoughAndWwwIsIgnored() = runTest {
        val server = matchingServer().apply {
            byItunesId = {
                respondJson(feedBody(101, "https://cdn.other.example.test/rss", originalUrl = "https://www.anchor.example.test/old"))
            }
        }
        val fixture = Fixture(server)

        assertEquals(NewsSourcePath.ITUNES_ID, fixture.resolver.resolve(vov).resolved().path)
        assertEquals(listOf("podcasts/byitunesid"), fixture.paths())
    }

    @Test
    fun itunesFeedOnAnotherHostFallsBackToFeedUrl() = runTest {
        val server = matchingServer().apply {
            byItunesId = { respondJson(feedBody(666, "https://impostor.example.test/anchor.example.test/rss")) }
        }
        val fixture = Fixture(server)

        val result = fixture.resolver.resolve(vov).resolved()

        assertEquals(202, result.feed.id, "the impostor feed is never used")
        assertEquals(NewsSourcePath.FEED_URL, result.path)
        assertEquals(listOf("podcasts/byitunesid", "podcasts/byfeedurl"), fixture.paths())
        assertEquals(vov.feedUrl, fixture.mock.requests.last().url.parameters["url"])
    }

    @Test
    fun itunesErrorOrEmptyFallsBackToFeedUrl() = runTest {
        listOf<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            { respondJson("{}", HttpStatusCode.InternalServerError) },
            { respondJson("""{"status":"true","feed":[]}""") },
        ).forEach { answer ->
            val fixture = Fixture(matchingServer().apply { byItunesId = answer })

            assertEquals(NewsSourcePath.FEED_URL, fixture.resolver.resolve(vov).resolved().path)
            assertEquals(listOf("podcasts/byitunesid", "podcasts/byfeedurl"), fixture.paths())
        }
    }

    @Test
    fun withoutItunesIdOnlyFeedUrlIsAsked() = runTest {
        val fixture = Fixture(matchingServer().apply { byFeedUrl = { respondJson(feedBody(303, night.feedUrl)) } })

        val result = fixture.resolver.resolve(night).resolved()

        assertEquals(303, result.feed.id)
        assertEquals(listOf("podcasts/byfeedurl"), fixture.paths())
    }

    @Test
    fun resultIsCachedSevenDaysOnTheDevice() = runTest {
        val fixture = Fixture(matchingServer())

        fixture.resolver.resolve(vov)
        fixture.advance(7.days)
        val cached = fixture.resolver.resolve(vov).resolved()

        assertEquals(NewsSourcePath.CACHE, cached.path)
        assertEquals(101, cached.feed.id)
        assertEquals(1, fixture.mock.requests.size, "a second resolve within 7 days sends nothing")
        assertNotNull(fixture.store.getString("newsHub.feed.vov"))

        fixture.advance(1.milliseconds)
        assertEquals(NewsSourcePath.ITUNES_ID, fixture.resolver.resolve(vov).resolved().path)
        assertEquals(2, fixture.mock.requests.size, "7 days + 1 ms later it resolves again")
    }

    @Test
    fun aNewResolverOnTheSameStoreUsesTheCache() = runTest {
        val first = Fixture(matchingServer())
        first.resolver.resolve(vov)

        val relaunch = Fixture(matchingServer(), store = first.store, clock = FixedClock(NOW + 3.days.inWholeMilliseconds))
        val result = relaunch.resolver.resolve(vov).resolved()

        assertEquals(NewsSourcePath.CACHE, result.path)
        assertTrue(relaunch.mock.requests.isEmpty())
    }

    @Test
    fun aChangedFeedUrlInTheConfigIgnoresTheCache() = runTest {
        val fixture = Fixture(matchingServer())
        fixture.resolver.resolve(vov)

        fixture.resolver.resolve(vov.copy(itunesId = null))

        assertEquals(listOf("podcasts/byitunesid", "podcasts/byfeedurl"), fixture.paths())
    }

    @Test
    fun notFoundIsUnresolvedAndReportedOncePerProcess() = runTest {
        val fixture = Fixture(
            matchingServer().apply {
                byItunesId = { respondJson("""{"status":"true","feed":[]}""") }
                byFeedUrl = { respondJson(notFound, HttpStatusCode.BadRequest) }
            },
        )

        assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
        assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
        assertEquals(2, fixture.mock.requests.size, "a not-found answer is reused in memory")

        fixture.advance(15.minutes + 1.seconds)
        assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
        assertEquals(4, fixture.mock.requests.size, "and asked again later")

        assertEquals(listOf("vov"), fixture.reported)
        assertEquals(listOf("news_source_unresolved key=vov"), fixture.crash.logs)
        assertEquals(null, fixture.store.getString("newsHub.feed.vov"), "a negative result never reaches the disk")
    }

    @Test
    fun deadFeedIsUnresolved() = runTest {
        val fixture = Fixture(
            matchingServer().apply { byItunesId = { respondJson(feedBody(101, vov.feedUrl, dead = 1)) } },
        )

        assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
        assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
        assertEquals(listOf("vov"), fixture.reported)
    }

    @Test
    fun blockedFeedIsUnresolved() = runTest {
        val byHost = Fixture(matchingServer(), blacklist = """{"feedId":[],"filterText":[],"host":["anchor.example.test"]}""")
        val byId = Fixture(matchingServer(), blacklist = """{"feedId":["101"],"filterText":[]}""")
        val byAuthor = Fixture(
            matchingServer().apply { byItunesId = { respondJson(feedBody(101, vov.feedUrl, extra = ""","author":"Spam Media"""")) } },
            blacklist = """{"feedId":[],"filterText":[],"author":["spam media"]}""",
        )

        listOf(byHost, byId, byAuthor).forEach { fixture ->
            assertIs<NewsSourceResolution.Unresolved>(fixture.resolver.resolve(vov))
            assertEquals(listOf("vov"), fixture.reported)
        }
    }

    @Test
    fun networkErrorsAreNeitherCachedNorReported() = runTest {
        val fixture = Fixture(matchingServer().apply { byFeedUrl = { respondJson("{}", HttpStatusCode.ServiceUnavailable) } })

        assertIs<NewsSourceResolution.Failed>(fixture.resolver.resolve(night))
        assertIs<NewsSourceResolution.Failed>(fixture.resolver.resolve(night))

        assertEquals(2, fixture.mock.requests.size)
        assertTrue(fixture.reported.isEmpty())
        assertTrue(fixture.crash.logs.isEmpty())
    }

    // The resolver inside the real graph: the hub's episodes, the other sources, the request set.

    private fun episodes(feedId: Long, count: Int) = (1..count).joinToString(",", """{"count":$count,"items":[""", "]}") { i ->
        """{"id":${feedId * 100 + i},"feedId":$feedId,"title":"Bản tin $i","datePublished":${NOW / 1000 - i * 600},
            "duration":300,"enclosureUrl":"https://media.example.test/$feedId/$i.mp3"}"""
    }

    private val hubConfig = """{"version":1,"enabled":true,"sections":[
        {"id":"news","sources":[
          {"key":"vov","feedUrl":"https://anchor.example.test/s/86ec8d4/podcast/rss","itunesId":1448565355,"confirmedAt":"2026-11-02","brief":{"take":1}},
          {"key":"gone","feedUrl":"https://gone.example.test/rss","confirmedAt":"2026-11-02","brief":{"take":1}}]},
        {"id":"night","sleepTimerMinutes":30,"sources":[
          {"key":"night","feedUrl":"https://www.night.example.test/podcast/rss","confirmedAt":"2026-11-02"}]}]}"""

    private fun hubGraph() = TestGraph(
        config = FakeConfig(mapOf(ConfigKeys.KEY_NEWS_HUB to hubConfig)),
        clock = FixedClock(NOW),
        handler = { request ->
            when (request.path()) {
                "podcasts/byitunesid" -> respondJson(feedBody(101, "https://anchor.example.test/s/86ec8d4/podcast/rss"))
                "podcasts/byfeedurl" -> when (request.url.parameters["url"]) {
                    "https://www.night.example.test/podcast/rss" -> respondJson(feedBody(303, "https://night.example.test/podcast/rss"))
                    else -> respondJson(notFound, HttpStatusCode.BadRequest)
                }

                "episodes/byfeedid" -> respondJson(episodes(request.url.parameters["id"]!!.toLong(), 20))
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        },
    )

    @Test
    fun hubAsksTwentyEpisodesPerSourceCachedFifteenMinutes() = runTest {
        val test = hubGraph()

        test.graph.newsHub.load(NOW)
        test.advance(14.minutes + 59.seconds)
        test.graph.newsHub.load(test.clock.millis)

        val episodeRequests = test.requests.filter { it.path() == "episodes/byfeedid" }
        assertEquals(listOf("101", "303"), episodeRequests.map { it.url.parameters["id"].orEmpty() }.sorted())
        assertTrue(episodeRequests.all { it.url.parameters["max"] == "20" })

        test.advance(2.seconds)
        test.graph.newsHub.load(test.clock.millis)
        assertEquals(4, test.requests.count { it.path() == "episodes/byfeedid" }, "15:01 later the episodes are asked again")
    }

    @Test
    fun unresolvedSourceIsDroppedAndReportedOnceWhileTheOthersStay() = runTest {
        val test = hubGraph()
        val reported = mutableListOf<String>()
        test.graph.newsHub.unresolvedListener = NewsSourceUnresolvedListener { reported += it }

        val first = assertIs<TSDataState.Success<NewsHub>>(test.graph.newsHub.load(NOW)).data
        test.advance(20.minutes)
        val second = assertIs<TSDataState.Success<NewsHub>>(test.graph.newsHub.load(test.clock.millis)).data

        listOf(first, second).forEach { hub ->
            assertEquals(listOf("vov"), hub.news.map { it.key })
            assertEquals(listOf("night"), hub.night.map { it.key })
        }
        assertEquals(listOf("gone"), reported)
        assertEquals(listOf("news_source_unresolved key=gone"), test.crash.logs.filter { it.startsWith("news_source") })
    }

    @Test
    fun everyRequestGoesToPodcastIndexOnly() = runTest {
        val test = hubGraph()

        test.graph.newsHub.load(NOW)
        test.advance(8.days)
        test.graph.newsHub.load(test.clock.millis)

        assertTrue(test.requests.isNotEmpty())
        test.requests.forEach { request ->
            assertEquals("api.example.test", request.url.host, "${request.url}")
            assertTrue(request.path() in allowedPaths, "${request.url}")
        }
        val publisherHosts = setOf("anchor.example.test", "gone.example.test", "night.example.test", "media.example.test")
        assertTrue(test.requests.none { it.url.host in publisherHosts || it.url.host.endsWith(".night.example.test") })
    }

    private companion object {
        /** 2026-10-30 07:00 UTC+7. */
        const val NOW = 1_793_318_400_000L
    }
}
