package tss.t.shared.model

import tss.t.shared.fixtures.Fixtures
import tss.t.shared.generated.BundledAssets
import tss.t.shared.network.PodcastIndexJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real payloads from `podcasts/src/debug/assets` (generated into [Fixtures] by
 * `:shared:generateTestFixtures`) and hand-written edge cases.
 */
class ModelParsingTest {

    private val json = PodcastIndexJson

    @Test
    fun trendingFixtureParses() {
        val res = json.decodeFromString(TrendingPodcastRes.serializer(), Fixtures.trending)

        assertEquals(100, res.count)
        assertEquals(100, res.items.size)
        assertEquals(100, res.max)
        val first = res.items.first()
        assertEquals(5675247L, first.id)
        assertEquals("Wellbeing Wisdom Tiny Bites", first.title)
        assertEquals("Sandy Weaver", first.author)
        assertNull(first.feedId)
        assertEquals(0, first.explicit)
        val withCategories = res.items.first { !it.categories.isNullOrEmpty() }
        assertTrue(withCategories.categories!!.isNotEmpty())
        assertTrue(withCategories.categories.keys.all { key -> key.all { it.isDigit() } })
    }

    @Test
    fun episodeFixtureParses() {
        val res = json.decodeFromString(EpisodeResponse.serializer(), Fixtures.episode)

        assertEquals(100, res.count)
        assertTrue(res.items.isNotEmpty())
        assertEquals("64676", res.query)
        assertEquals(emptyList(), res.liveItems)
        assertTrue(res.items.all { it.id != 0L && it.enclosureUrl.isNotBlank() })
    }

    @Test
    fun liveEpisodeFixtureParses() {
        val res = json.decodeFromString(LiveResponse.serializer(), Fixtures.liveEpidsode)

        assertEquals(40, res.count)
        assertEquals(40, res.items.size)
        // The API sends "max": "100" (a string) here.
        assertEquals(100, res.max)
        // Two items carry "categories": [] instead of an object.
        assertTrue(res.items.any { it.categories != null && it.categories.isEmpty() })
        assertTrue(res.items.any { !it.categories.isNullOrEmpty() })
    }

    @Test
    fun recentFeedsFixtureParses() {
        val res = json.decodeFromString(TrendingPodcastRes.serializer(), Fixtures.recentFeeds)

        assertEquals(26, res.count)
        assertEquals(26, res.items.size)
        assertTrue(res.items.all { it.id != 0L })
    }

    @Test
    fun searchResponseHasBooleanExplicitAndCategoriesMap() {
        val res = json.decodeFromString(
            SearchResponse.serializer(),
            """
            {
              "status": "true",
              "feeds": [
                {
                  "id": 920666,
                  "title": "Podcasting 2.0",
                  "url": "https://feeds.example.test/pc20.xml",
                  "image": "https://example.test/image.jpg",
                  "artwork": "https://example.test/artwork.jpg",
                  "explicit": true,
                  "itunesId": null,
                  "categories": {"1": "Arts"},
                  "funding": {"message": "Support", "url": "https://example.test/fund"},
                  "value": {
                    "model": {"type": "lightning", "method": "keysend", "suggested": "0.00000005000"},
                    "destinations": [{"name": "Host", "address": "abc", "type": "node", "split": 99}]
                  }
                }
              ],
              "count": 1,
              "query": "podcasting",
              "description": "Found matching feeds"
            }
            """.trimIndent(),
        )

        val feed = res.feeds.single()
        assertTrue(feed.explicit)
        assertEquals(mapOf("1" to "Arts"), feed.categories)
        assertNull(feed.itunesId)
        assertEquals("Support", feed.funding?.message)
        assertEquals(99, feed.value?.destinations?.single()?.split)
        assertEquals("podcasting", res.query)
    }

    @Test
    fun explicitIsIntOnPodcastAndEpisodeButBooleanOnFeed() {
        val podcast = json.decodeFromString(Podcast.serializer(), """{"id":1,"explicit":1}""")
        val episode = json.decodeFromString(Episode.serializer(), """{"id":1,"explicit":1}""")
        val live = json.decodeFromString(LiveEpisode.serializer(), """{"id":1,"explicit":1}""")
        val feed = json.decodeFromString(Feed.serializer(), """{"id":1,"explicit":false}""")

        assertEquals(1, podcast.explicit)
        assertEquals(1, episode.explicit)
        assertEquals(1, live.explicit)
        assertFalse(feed.explicit)
    }

    @Test
    fun nullAndMissingFieldsFallBackToDefaults() {
        val episode = json.decodeFromString(
            Episode.serializer(),
            """{"id":7,"title":null,"duration":null,"image":null,"feedImage":"https://f.test/a.png"}""",
        )

        assertEquals("", episode.title)
        assertEquals(0L, episode.duration)
        assertEquals("https://f.test/a.png", episode.getImageUrl())
    }

    @Test
    fun podcastByFeedIdParses() {
        val res = json.decodeFromString(
            PodcastByFeedIdRes.serializer(),
            """
            {
              "status": "true",
              "query": {"id": "75075"},
              "feed": {"id": 75075, "title": "Batman University", "explicit": false, "categories": {"55": "News"}},
              "description": "Found matching feed"
            }
            """.trimIndent(),
        )

        assertEquals("75075", res.query?.id)
        assertEquals(75075L, res.feed?.id)
        assertEquals(mapOf("55" to "News"), res.feed?.categories)
    }

    @Test
    fun podcastByFeedIdUnknownIdGivesNullFeed() {
        val res = json.decodeFromString(
            PodcastByFeedIdRes.serializer(),
            """{"status":"true","query":{"id":"0"},"feed":[],"description":"No feeds match this id."}""",
        )

        assertNull(res.feed)
        assertEquals("0", res.query?.id)
    }

    @Test
    fun categoriesApiResponseParses() {
        val res = json.decodeFromString(
            CategoryRes.serializer(),
            """{"status":"true","feeds":[{"id":1,"name":"Arts"},{"id":2,"name":"Books"}],"count":2}""",
        )

        assertEquals(2, res.count)
        assertEquals(CategoryRes.Category(id = 2, name = "Books"), res.feeds[1])
    }

    @Test
    fun bundledCategoriesAssetHas112Categories() {
        val res = CategoryRes.fromAssetJson(json, BundledAssets.categories)

        assertEquals(112, res.count)
        assertEquals(112, res.feeds.size)
        assertEquals(CategoryRes.Category(id = 1, name = "Arts"), res.feeds.first())
    }

    @Test
    fun podcastFromFeedKeepsIdTitleAndImage() {
        val feed = Feed(
            id = 42L,
            title = "Nhạc Việt",
            image = "https://example.test/image.jpg",
            artwork = "https://example.test/artwork.jpg",
            author = "TS",
            explicit = true,
            categories = mapOf("8" to "Music"),
            podcastGuid = "guid-42",
        )

        val podcast = Podcast.fromFeed(feed)

        assertEquals(42L, podcast.id)
        assertEquals(42L, podcast.feedId)
        assertEquals("Nhạc Việt", podcast.title)
        // Same as Android: image takes the artwork, feedImage the channel image.
        assertEquals("https://example.test/artwork.jpg", podcast.image)
        assertEquals("https://example.test/image.jpg", podcast.feedImage)
        assertEquals(1, podcast.explicit)
        assertEquals(mapOf("8" to "Music"), podcast.categories)
        assertEquals("guid-42", podcast.guid)
        assertEquals("TS", podcast.author)
    }

    @Test
    fun episodeImageFallsBackToFeedImageWhenBlank() {
        val episode = Episode(image = "  ", feedImage = "https://example.test/feed.jpg")

        assertEquals("https://example.test/feed.jpg", episode.getImageUrl())
        assertEquals(
            "https://example.test/ep.jpg",
            episode.copy(image = " https://example.test/ep.jpg ").getImageUrl(),
        )
    }

    @Test
    fun categoriesArrayFormIsReadAsIndexMap() {
        val podcast = json.decodeFromString(Podcast.serializer(), """{"id":1,"categories":["Arts","News"]}""")
        val numeric = json.decodeFromString(Podcast.serializer(), """{"id":1,"categories":{"9":"Leisure","10":null}}""")
        val absent = json.decodeFromString(Podcast.serializer(), """{"id":1,"categories":null}""")

        assertEquals(mapOf("0" to "Arts", "1" to "News"), podcast.categories)
        assertEquals(mapOf("9" to "Leisure"), numeric.categories)
        assertNotNull(podcast.categories)
        assertNull(absent.categories)
    }
}
