package tss.t.shared.deeplink

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.model.Podcast
import tss.t.shared.model.TSDataState
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** M4-AC20 (shared part): what a podcast/episode link opens, after the blacklist and D6. */
class DeepLinkTargetsTest {

    private val episodeBody = """{"episode":{"id":456,"feedId":42,"title":"Tập 1","explicit":0,"feedImage":"https://img/42.jpg"}}"""
    private val explicitEpisodeBody = """{"episode":{"id":456,"feedId":42,"title":"Tập 1","explicit":1}}"""
    private val feedBody = """{"feed":{"id":42,"title":"Show","explicit":false,"artwork":"https://img/a.jpg"},"query":{"id":"42"}}"""
    private val explicitFeedBody = """{"feed":{"id":42,"title":"Show","explicit":true},"query":{"id":"42"}}"""
    private val unknownFeedBody = """{"feed":[],"query":{"id":"42"}}"""

    private fun HttpRequestData.path() = url.encodedPath.substringAfter("/api/1.0/")

    /** E12 answers [episode], E11 answers [feed]; null is HTTP 500. */
    private fun graph(
        episode: String? = episodeBody,
        feed: String? = feedBody,
        blacklist: String? = null,
    ) = TestGraph(config = FakeConfig.blacklist(blacklist), handler = { request ->
        val body = if (request.path() == "episodes/byid") episode else feed
        if (body == null) respondJson("{}", HttpStatusCode.InternalServerError) else respondJson(body)
    })

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    private fun TSDataState<*>.refusal(): DeepLinkTargets.Reason =
        assertIs<DeepLinkRefusedException>(assertIs<TSDataState.Error<*>>(this).exception).reason

    @Test
    fun podcastLinkUsesTheE11Feed() = runTest {
        val test = graph()

        val podcast = test.graph.deepLinkTargets.podcast(42).data()

        assertEquals(42L, podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals(1, test.requestsTo("podcasts/byfeedid"))
    }

    @Test
    fun podcastLinkFallsBackToTheSavedPodcastOffline() = runTest {
        val test = graph(feed = null)
        test.graph.podcastStorage.insertPodcast(Podcast(id = 42, title = "Saved show"))

        assertEquals("Saved show", test.graph.deepLinkTargets.podcast(42).data().title)
    }

    @Test
    fun podcastLinkWithNothingKnownIsAnError() = runTest {
        val test = graph(feed = unknownFeedBody)

        val error = assertIs<TSDataState.Error<*>>(test.graph.deepLinkTargets.podcast(42))

        assertIs<NoSuchElementException>(error.exception)
    }

    @Test
    fun blacklistedPodcastIsRefusedWithoutARequest() = runTest {
        val test = graph(blacklist = """{"feedId":["42"],"filterText":[]}""")

        assertEquals(DeepLinkTargets.Reason.Blacklisted, test.graph.deepLinkTargets.podcast(42).refusal())
        assertEquals(0, test.requests.size)
    }

    @Test
    fun podcastWithABlacklistedKeywordIsRefused() = runTest {
        val test = graph(blacklist = """{"feedId":[],"filterText":["show"]}""")

        assertEquals(DeepLinkTargets.Reason.Blacklisted, test.graph.deepLinkTargets.podcast(42).refusal())
    }

    @Test
    fun explicitPodcastIsLeftToDetail() = runTest {
        val test = graph(feed = explicitFeedBody)

        assertEquals(1, test.graph.deepLinkTargets.podcast(42).data().explicit)
    }

    @Test
    fun episodeLinkComesWithTheE11Podcast() = runTest {
        val test = graph()

        val target = test.graph.deepLinkTargets.episode(456).data()

        assertEquals(456L, target.episode.id)
        assertEquals(42L, target.podcast.id)
        assertEquals("Show", target.podcast.title)
        assertEquals(1, test.requestsTo("episodes/byid"))
    }

    @Test
    fun episodeLinkFallsBackToTheSavedPodcastThenToTheEpisode() = runTest {
        val saved = graph(feed = null)
        saved.graph.podcastStorage.insertPodcast(Podcast(id = 42, title = "Saved show"))
        assertEquals("Saved show", saved.graph.deepLinkTargets.episode(456).data().podcast.title)

        val bare = graph(feed = unknownFeedBody).graph.deepLinkTargets.episode(456).data().podcast
        assertEquals(42L, bare.id)
        assertEquals(42L, bare.feedId)
        assertEquals("https://img/42.jpg", bare.image)
    }

    @Test
    fun episodeErrorsKeepTheirException() = runTest {
        val unknown = graph(episode = """{"episode":[]}""").graph.deepLinkTargets.episode(9)
        assertIs<NoSuchElementException>(assertIs<TSDataState.Error<*>>(unknown).exception)

        val failed = graph(episode = null).graph.deepLinkTargets.episode(9)
        assertIs<TSDataState.Error<*>>(failed)
    }

    @Test
    fun blacklistedEpisodeIsRefused() = runTest {
        val byFeed = graph(blacklist = """{"feedId":["42"],"filterText":[]}""")
        assertEquals(DeepLinkTargets.Reason.Blacklisted, byFeed.graph.deepLinkTargets.episode(456).refusal())

        val byKeyword = graph(blacklist = """{"feedId":[],"filterText":["tập"]}""")
        assertEquals(DeepLinkTargets.Reason.Blacklisted, byKeyword.graph.deepLinkTargets.episode(456).refusal())

        val byFeedTitle = graph(blacklist = """{"feedId":[],"filterText":["show"]}""")
        assertEquals(DeepLinkTargets.Reason.Blacklisted, byFeedTitle.graph.deepLinkTargets.episode(456).refusal())
    }

    @Test
    fun explicitEpisodeOrFeedIsRefusedUnlessAllowed() = runTest {
        val episode = graph(episode = explicitEpisodeBody)
        assertEquals(DeepLinkTargets.Reason.Explicit, episode.graph.deepLinkTargets.episode(456).refusal())

        val feed = graph(feed = explicitFeedBody)
        assertEquals(DeepLinkTargets.Reason.Explicit, feed.graph.deepLinkTargets.episode(456).refusal())

        val allowed = graph(episode = explicitEpisodeBody, feed = explicitFeedBody)
        allowed.graph.appPreferences.saveAllowExplicitContent(true)
        assertEquals(456L, allowed.graph.deepLinkTargets.episode(456).data().episode.id)
    }
}
