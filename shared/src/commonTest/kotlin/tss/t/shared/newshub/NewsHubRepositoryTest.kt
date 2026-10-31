package tss.t.shared.newshub

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.config.ConfigKeys
import tss.t.shared.model.TSDataState
import tss.t.shared.network.FixedClock
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** M6-AC16: "Mới nhất", the brief and the per-source filters of the loaded hub. */
class NewsHubRepositoryTest {

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")

    /** Feed id per source key; the feed URL is `https://<key>.example.test/rss`. */
    private val feedIds = mapOf("a" to 1L, "b" to 2L, "c" to 3L, "n" to 9L, "x" to 7L, "empty" to 8L)

    private fun source(key: String, confirmedAt: String? = "2026-11-02", brief: String = """{"take":1}""") =
        """{"key":"$key","feedUrl":"https://$key.example.test/rss","confirmedAt":${confirmedAt?.let { "\"$it\"" } ?: "null"},"brief":$brief}"""

    private fun hub(news: List<String>, night: List<String> = emptyList(), enabled: Boolean = true, minutes: Int = 45) =
        """{"version":1,"enabled":$enabled,"sections":[
            {"id":"news","sources":[${news.joinToString(",")}]},
            {"id":"night","sleepTimerMinutes":$minutes,"sources":[${night.joinToString(",")}]}]}"""

    /**
     * Feed [id] has [count] episodes, the i-th published `i * 7 + id` minutes before [NOW], so
     * the feeds interleave; every third episode of feed 3 is explicit.
     */
    private fun episodes(id: Long, count: Int) = (1..count).joinToString(",", """{"count":$count,"items":[""", "]}") { i ->
        val explicit = if (id == 3L && i % 3 == 0) 1 else 0
        """{"id":${id * 1000 + i},"feedId":$id,"title":"Feed $id tập $i","duration":60,"explicit":$explicit,
            "datePublished":${(NOW - (i * 7 + id) * MINUTE) / 1000},"enclosureUrl":"https://media.example.test/$id/$i.mp3"}"""
    }

    private fun graph(
        config: String,
        explicitFeeds: Set<Long> = emptySet(),
        failing: Set<Long> = emptySet(),
        failResolve: Boolean = false,
    ) = TestGraph(
        config = FakeConfig(mapOf(ConfigKeys.KEY_NEWS_HUB to config)),
        clock = FixedClock(NOW),
        handler = { request ->
            when (request.path()) {
                "podcasts/byfeedurl" -> {
                    val url = request.url.parameters["url"].orEmpty()
                    val key = url.removePrefix("https://").substringBefore(".example.test")
                    val id = feedIds.getValue(key)
                    if (failResolve) {
                        respondJson("{}", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respondJson("""{"feed":{"id":$id,"title":"Feed $key","url":"$url","explicit":${id in explicitFeeds}}}""")
                    }
                }

                "episodes/byfeedid" -> {
                    val id = request.url.parameters["id"]!!.toLong()
                    when {
                        id in failing -> respondJson("{}", HttpStatusCode.InternalServerError)
                        id == 8L -> respondJson("""{"count":0,"items":[]}""")
                        else -> respondJson(episodes(id, if (id == 3L) 6 else 20))
                    }
                }

                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        },
    )

    private suspend fun TestGraph.hub(now: Long = NOW): NewsHub = assertIs<TSDataState.Success<NewsHub>>(graph.newsHub.load(now)).data

    @Test
    fun latestIsThirtyNewestNewsEpisodesNewestFirst() = runTest {
        val hub = graph(hub(listOf(source("a"), source("b"), source("c")), listOf(source("n")))).hub()

        assertEquals(30, hub.latest.size)
        val dates = hub.latest.map { it.episode.datePublished }
        assertEquals(dates.sortedDescending(), dates)
        assertTrue(hub.latest.none { it.episode.feedId == 9L }, "only the news section")
        assertTrue(hub.latest.all { it.feed.id == it.episode.feedId })
        assertEquals(setOf("a", "b", "c"), hub.latest.map { it.sourceKey }.toSet())
    }

    @Test
    fun latestHasFewerWhenTheSourcesHaveFewer() = runTest {
        val hub = graph(hub(listOf(source("c")))).hub()

        assertEquals(4, hub.latest.size, "6 episodes, 2 explicit dropped by D6")
    }

    @Test
    fun sectionsKeepConfigOrderAndTheNightTimer() = runTest {
        val hub = graph(hub(listOf(source("b"), source("a")), listOf(source("n")))).hub()

        assertTrue(hub.isEnabled)
        assertFalse(hub.isEmpty)
        assertEquals(listOf("b", "a"), hub.news.map { it.key })
        assertEquals(listOf("n"), hub.night.map { it.key })
        assertEquals(45, hub.nightSleepTimerMinutes)
        assertEquals(listOf("b", "a"), hub.brief.items.map { it.sourceKey }, "the brief follows config order")
        assertTrue(hub.news.all { source -> source.episodes.zipWithNext().all { (x, y) -> x.datePublished >= y.datePublished } })
    }

    @Test
    fun disabledHubSendsNoRequest() = runTest {
        val test = graph(hub(listOf(source("a")), enabled = false))

        assertSame(NewsHub.Disabled, test.hub())
        assertTrue(NewsHub.Disabled.isEmpty)
        assertFalse(test.graph.newsHub.isEnabled)
        assertTrue(test.requests.isEmpty())
    }

    @Test
    fun unconfirmedSourceNeverAppears() = runTest {
        val test = graph(hub(listOf(source("a"), source("x", confirmedAt = null))))
        val hub = test.hub()

        assertEquals(listOf("a"), hub.news.map { it.key })
        assertTrue(hub.brief.items.none { it.sourceKey == "x" })
        assertTrue(test.requests.none { it.url.parameters["url"]?.contains("x.example.test") == true })
    }

    @Test
    fun hiddenShowIsLeftOut() = runTest {
        val test = graph(hub(listOf(source("a"), source("b"))))
        test.graph.hiddenShows.hide(feedId = 2, title = "Feed b", imageUrl = null)

        val hub = test.hub()

        assertEquals(listOf("a"), hub.news.map { it.key })
        assertTrue(hub.latest.none { it.episode.feedId == 2L })
        assertTrue(hub.brief.items.none { it.feed.id == 2L })
    }

    @Test
    fun explicitContentIsFilteredByDefault() = runTest {
        val test = graph(hub(listOf(source("a"), source("c"))), explicitFeeds = setOf(1L))

        val hub = test.hub()
        assertEquals(listOf("c"), hub.news.map { it.key }, "an explicit feed is left out")
        assertTrue(hub.news.single().episodes.none { it.explicit == 1 })

        test.graph.appPreferences.saveAllowExplicitContent(true)
        val allowed = test.hub()
        assertEquals(listOf("a", "c"), allowed.news.map { it.key })
        assertEquals(6, allowed.news.last().episodes.size)
    }

    @Test
    fun sourceWithoutEpisodesIsLeftOut() = runTest {
        val hub = graph(hub(listOf(source("a"), source("empty")))).hub()

        assertEquals(listOf("a"), hub.news.map { it.key })
    }

    @Test
    fun oneFailingSourceDoesNotHideTheOthers() = runTest {
        val hub = graph(hub(listOf(source("a"), source("b"))), failing = setOf(1L)).hub()

        assertEquals(listOf("b"), hub.news.map { it.key })
    }

    @Test
    fun everySourceFailingIsAnError() = runTest {
        assertIs<TSDataState.Error<NewsHub>>(graph(hub(listOf(source("a"))), failing = setOf(1L)).graph.newsHub.load(NOW))
        assertIs<TSDataState.Error<NewsHub>>(graph(hub(listOf(source("a"))), failResolve = true).graph.newsHub.load(NOW))
    }

    @Test
    fun briefUsesTheGivenTime() = runTest {
        val test = graph(hub(listOf(source("a", brief = """{"take":1,"maxAgeHours":1}"""))))

        assertEquals(1, test.hub(NOW).brief.items.size)
        assertTrue(test.hub(NOW + 3 * 60 * MINUTE).brief.isEmpty, "three hours later nothing is within one hour")
    }

    @Test
    fun reloadingTheConfigAppliesOnTheNextLoad() = runTest {
        val config = FakeConfig(mapOf(ConfigKeys.KEY_NEWS_HUB to hub(listOf(source("a")))))
        val test = TestGraph(config = config, clock = FixedClock(NOW))
        assertTrue(test.graph.newsHub.isEnabled)

        config.values = mapOf(ConfigKeys.KEY_NEWS_HUB to hub(listOf(source("a")), enabled = false))
        test.graph.reloadRemoteConfig()

        assertFalse(test.graph.newsHub.isEnabled)
        assertSame(NewsHub.Disabled, test.hub())
    }

    private companion object {
        const val MINUTE = 60_000L

        /** 2026-10-30 07:00 UTC+7. */
        const val NOW = 1_793_318_400_000L
    }
}
