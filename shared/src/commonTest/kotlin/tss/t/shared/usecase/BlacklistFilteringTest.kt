package tss.t.shared.usecase

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Blacklist per endpoint (M0 §3.4) with the override `{"feedId":["111"],"filterText":["casino"]}`.
 * Every test uses a fresh graph, so each one is also "the first call after startup".
 */
class BlacklistFilteringTest {

    private fun graph(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = TestGraph(
        config = FakeConfig.blacklist("""{"feedId":["111"],"filterText":["casino"]}"""),
        handler = handler,
    ).graph

    private fun <T : Any> TSDataState<T>.data(): T = assertIs<TSDataState.Success<T>>(this).data

    @Test
    fun trendingDropsIdFeedIdTitleAndDescriptionMatches() = runTest {
        val graph = graph { respondJson(TRENDING) }

        val result = graph.getTrendingPodcasts(max = 100, since = 1L).data()

        assertEquals(listOf(5L), result.items.map { it.id })
        assertEquals(1, result.count)
    }

    @Test
    fun firstCallRightAfterCreatingTheGraphIsFiltered() = runTest {
        // Android loads the list asynchronously in init, so this call could get through.
        val result = graph { respondJson(TRENDING) }
            .getTrendingPodcasts(max = 100, since = 1L)
            .data()

        assertEquals(listOf(5L), result.items.map { it.id })
    }

    @Test
    fun firstCallWithTheBundledAssetIsFiltered() = runTest {
        val graph = TestGraph(handler = {
            respondJson("""{"count":2,"feeds":[{"id":7101342,"title":"a"},{"id":9,"title":"b"}]}""")
        }).graph

        val result = graph.getTrendingPodcasts(max = 100, since = 1L).data()

        assertEquals(listOf(9L), result.items.map { it.id })
    }

    @Test
    fun liveDropsFeedIdTitleAndDescriptionButNotEpisodeId() = runTest {
        val graph = graph { respondJson(LIVE) }

        val result = graph.getLiveEpisodes().data()

        assertEquals(listOf(4L, 111L), result.items.map { it.id })
        assertEquals(2, result.count)
    }

    @Test
    fun recentFeedsDropIdTitleAndDescriptionButNotFeedId() = runTest {
        val graph = graph { respondJson(RECENT) }

        val result = graph.getRecentFeeds(cat = null).data()

        assertEquals(listOf(4L, 5L), result.items.map { it.id })
        assertEquals(2, result.count)
    }

    @Test
    fun musicFeedsDropIdAndTitleButKeepDescriptionMatches() = runTest {
        val graph = graph { respondJson(FEEDS) }

        val result = graph.getMusicFeeds().data()

        assertEquals(listOf(3L, 4L), result.map { it.id })
    }

    @Test
    fun searchDropsIdAndTitleButKeepsDescriptionMatches() = runTest {
        val graph = graph { respondJson(FEEDS) }

        val result = graph.searchPodcasts(query = "news").data()

        assertEquals(listOf(3L, 4L), result.feeds.map { it.id })
        assertEquals(2, result.count)
    }

    @Test
    fun musicSearchDropsIdAndTitleButKeepsDescriptionMatches() = runTest {
        val graph = graph { respondJson(FEEDS) }

        val result = graph.searchMusicFeeds(query = "rock").data()

        assertEquals(listOf(3L, 4L), result.feeds.map { it.id })
        assertEquals(2, result.count)
    }

    @Test
    fun categoriesEpisodesAndPodcastByFeedAreNotFiltered() = runTest {
        val graph = graph { request ->
            when {
                request.url.encodedPath.endsWith("categories/list") -> respondJson(
                    """{"count":2,"feeds":[{"id":1,"name":"Casino"},{"id":111,"name":"Arts"}]}"""
                )

                request.url.encodedPath.endsWith("episodes/byfeedid") -> respondJson(
                    """{"count":2,"items":[
                        {"id":1,"feedId":111,"title":"casino 1"},
                        {"id":2,"feedId":111,"title":"casino 2","description":"casino"}
                    ]}"""
                )

                else -> respondJson("""{"feed":{"id":111,"title":"Casino FM"},"query":{"id":"111"}}""")
            }
        }

        val categories = graph.getCategories().data()
        val episodes = graph.getEpisodeByFeedId("111").data()
        val podcast = graph.getPodcastByFeedID("111").data()

        assertEquals(listOf(1, 111), categories.feeds.map { it.id })
        assertEquals(2, categories.count)
        assertEquals(listOf(1L, 2L), episodes.items.map { it.id })
        assertEquals(2, episodes.count)
        assertEquals(111L, podcast.feed?.id)
    }

    // M6-AC10: host (feed or enclosure URL, subdomains too) and author, from the bundled extra.

    private fun spamGraph(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = TestGraph(
        config = FakeConfig.blacklist("""{"feedId":[],"filterText":[],"author":["Spam Author"]}"""),
        handler = handler,
    ).graph

    @Test
    fun trendingAndRecentDropBlockedHostsAndAuthors() = runTest {
        val trending = spamGraph { respondJson(SPAM_PODCASTS) }.getTrendingPodcasts(max = 100, since = 1L).data()
        val recent = spamGraph { respondJson(SPAM_PODCASTS) }.getRecentFeeds(cat = null).data()

        assertEquals(listOf(5L), trending.items.map { it.id })
        assertEquals(1, trending.count)
        assertEquals(listOf(5L), recent.items.map { it.id })
        assertEquals(1, recent.count)
    }

    @Test
    fun liveDropsBlockedEnclosureHosts() = runTest {
        val result = spamGraph { respondJson(SPAM_LIVE) }.getLiveEpisodes().data()

        assertEquals(listOf(3L), result.items.map { it.id })
        assertEquals(1, result.count)
    }

    @Test
    fun feedShapedResultsDropBlockedHostsAndAuthors() = runTest {
        val music = spamGraph { respondJson(SPAM_FEEDS) }.getMusicFeeds().data()
        val search = spamGraph { respondJson(SPAM_FEEDS) }.searchPodcasts(query = "q").data()
        val musicSearch = spamGraph { respondJson(SPAM_FEEDS) }.searchMusicFeeds(query = "q").data()

        assertEquals(listOf(6L), music.map { it.id })
        assertEquals(listOf(6L), search.feeds.map { it.id })
        assertEquals(1, search.count)
        assertEquals(listOf(6L), musicSearch.feeds.map { it.id })
    }

    private companion object {
        /** id 111, feedId 111, title, description, clean. */
        const val TRENDING = """{"count":5,"max":100,"feeds":[
            {"id":111,"title":"Morning news"},
            {"id":2,"feedId":111,"title":"Evening news"},
            {"id":3,"title":"Big CASINO night"},
            {"id":4,"title":"Sports","description":"Sponsored by a casino"},
            {"id":5,"title":"Clean show","description":"Talks about books"}
        ]}"""

        /** feedId 111, title, description, clean, and id 111 (kept: E2 checks feedId only). */
        const val LIVE = """{"count":5,"max":100,"items":[
            {"id":1,"feedId":111,"title":"Live 1"},
            {"id":2,"feedId":2,"title":"Casino live"},
            {"id":3,"feedId":3,"title":"Live 3","description":"play CASINO now"},
            {"id":4,"feedId":4,"title":"Clean live"},
            {"id":111,"feedId":5,"title":"Clean live with id 111"}
        ]}"""

        /** id 111, title, description, feedId 111 (kept: E5 checks id only), clean. */
        const val RECENT = """{"count":5,"max":100,"feeds":[
            {"id":111,"title":"Recent 1"},
            {"id":2,"title":"casino recent"},
            {"id":3,"title":"Recent 3","description":"casino"},
            {"id":4,"feedId":111,"title":"Recent 4"},
            {"id":5,"title":"Recent 5"}
        ]}"""

        /** Feed-shaped (E6–E8): id 111, title, description only (kept), clean. */
        const val FEEDS = """{"count":4,"query":"q","feeds":[
            {"id":111,"title":"Feed 1","explicit":false},
            {"id":2,"title":"Casino Royale FM","explicit":false},
            {"id":3,"title":"Feed 3","description":"casino talk","explicit":true},
            {"id":4,"title":"Feed 4","explicit":false}
        ]}"""

        /** Feed URL, subdomain feed URL, enclosure URL, author, and a clean one (E1/E5 shape). */
        const val SPAM_PODCASTS = """{"count":5,"feeds":[
            {"id":1,"title":"A","url":"https://ngocbaomedia.com/feed/1.xml"},
            {"id":2,"title":"B","url":"https://rss.ngocbaomedia.com/2"},
            {"id":3,"title":"C","url":"https://ok.example/3","enclosureUrl":"https://www.ngocbaomedia.com/3.mp3"},
            {"id":4,"title":"D","url":"https://ok.example/4","author":" spam author "},
            {"id":5,"title":"E","url":"https://notngocbaomedia.com/5","author":"Spam Author Fan"}
        ]}"""

        /** E2: the enclosure URL is the only URL. */
        const val SPAM_LIVE = """{"count":3,"items":[
            {"id":1,"feedId":10,"title":"L1","enclosureUrl":"https://ngocbaomedia.com/live.mp3"},
            {"id":2,"feedId":11,"title":"L2","enclosureUrl":"https://cdn.ngocbaomedia.com/live.mp3"},
            {"id":3,"feedId":12,"title":"L3","enclosureUrl":"https://ok.example/live.mp3"}
        ]}"""

        /** E6–E8: url, originalUrl, author, ownerName, and a clean one. */
        const val SPAM_FEEDS = """{"count":5,"feeds":[
            {"id":2,"title":"F2","url":"https://ngocbaomedia.com/rss"},
            {"id":3,"title":"F3","url":"https://ok.example/rss","originalUrl":"https://old.ngocbaomedia.com/rss"},
            {"id":4,"title":"F4","url":"https://ok.example/rss","author":"SPAM AUTHOR"},
            {"id":5,"title":"F5","url":"https://ok.example/rss","ownerName":"Spam Author"},
            {"id":6,"title":"F6","url":"https://ok.example/rss","author":"Someone"}
        ]}"""
    }
}
