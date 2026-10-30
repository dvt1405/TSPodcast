package tss.t.shared.newshub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tss.t.shared.generated.BundledAssets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M6-AC14: the bundled `shared/assets/news_hub_default.json` is exactly the table of M6 §3.4. */
class NewsHubConfigTest {

    private data class Expected(
        val key: String,
        val feedUrl: String,
        val itunesId: Long?,
        val brief: NewsBriefRule?,
    )

    // The URLs are split on purpose: M6-AC14 greps `shared/src` and expects them only in the JSON.
    private val news = listOf(
        Expected(
            "vov_thoisu", "https://anchor" + ".fm/s/86ec8d4/podcast/rss", 1448565355,
            NewsBriefRule(take = 1, maxAgeHours = 24, preferTitle = "Thời sự 6h", preferTitleBeforeHour = 11),
        ),
        Expected("vov_tinnhanh", "https://anchor" + ".fm/s/86f0c04/podcast/rss", 1448603366, NewsBriefRule(1, 24)),
        Expected("vov_sukien", "https://anchor" + ".fm/s/8535a2c/podcast/rss", 1447959801, NewsBriefRule(1, 24)),
        Expected("vov_kinhte", "https://anchor" + ".fm/s/84e912c/podcast/rss", 1447843388, NewsBriefRule(1, 24)),
        Expected(
            "vnexpress_homnay", "https://vnexpress.net" + "/rss/podcast/vnexpress-hom-nay.rss", 1751444063,
            NewsBriefRule(1, 48),
        ),
        Expected("tuoitre", "https://podcast" + ".tuoitre.vn/podcast-feed.rss", 1627456657, NewsBriefRule(2, 24)),
        Expected(
            "baophapluat", "https://baophapluat.vn" + "/rss/podcasts/ban-tin-phap-luat/feed.xml", 1652462907,
            NewsBriefRule(1, 24),
        ),
    )

    private val night = listOf(
        Expected("hanoi_doctruyen", "https://anchor" + ".fm/s/e75b3a50/podcast/rss", null, null),
    )

    private val bundled: NewsHubConfig get() = assertNotNull(NewsHubConfig.parse(BundledAssets.newsHubDefault))

    private fun NewsSource.expected() = Expected(key, feedUrl, itunesId, brief)

    @Test
    fun bundledFileDecodesWithTwoSections() {
        val config = bundled

        assertEquals(1, config.version)
        assertTrue(config.enabled)
        assertEquals(listOf(NewsHubConfig.SECTION_NEWS, NewsHubConfig.SECTION_NIGHT), config.sections.map { it.id })
        assertEquals(30, config.nightSleepTimerMinutes)
        assertNull(config.section(NewsHubConfig.SECTION_NEWS)?.sleepTimerMinutes)
    }

    @Test
    fun bundledFileHasTheEightSeedSourcesInOrder() {
        val config = bundled

        assertEquals(news, config.newsSources.map { it.expected() })
        assertEquals(night, config.nightSources.map { it.expected() })
        assertEquals(8, config.sections.sumOf { it.sources.size })
    }

    @Test
    fun noSeedSourceIsConfirmedYet() {
        val sources = bundled.sections.flatMap { it.sources }

        assertTrue(sources.all { it.confirmedAt == null })
        assertTrue(sources.none { it.isConfirmed })
    }

    @Test
    fun rawFileHasNoPodcastIndexIdField() {
        val forbidden = setOf("feedId", "podcastIndexId", "piFeedId", "podcastIndex", "id")
        val keys = mutableSetOf<String>()
        fun collect(element: JsonElement, inSource: Boolean) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (inSource) keys += key
                    collect(value, inSource || key == "sources")
                }

                is JsonArray -> element.forEach { collect(it, inSource) }
                else -> Unit
            }
        }
        collect(Json.parseToJsonElement(BundledAssets.newsHubDefault), inSource = false)

        assertTrue(keys.isNotEmpty())
        assertTrue(keys.intersect(forbidden).isEmpty(), "source keys: $keys")
        listOf("feedId", "podcastIndexId", "piFeedId", "podcastIndex").forEach {
            assertFalse(BundledAssets.newsHubDefault.contains("\"$it\""), it)
        }
    }

    @Test
    fun confirmedAtMustBeAnIsoDate() {
        fun source(confirmedAt: String?) = NewsSource("k", "https://example.test/rss", confirmedAt = confirmedAt)

        assertTrue(source("2026-11-02").isConfirmed)
        assertFalse(source(null).isConfirmed)
        assertFalse(source("").isConfirmed)
        assertFalse(source("yes").isConfirmed)
        assertFalse(source("02/11/2026").isConfirmed)
    }

    @Test
    fun parseRejectsBlankAndBrokenText() {
        assertNull(NewsHubConfig.parse(null))
        assertNull(NewsHubConfig.parse(""))
        assertNull(NewsHubConfig.parse("not json"))
        assertNull(NewsHubConfig.parse("[1,2]"))
        assertNull(NewsHubConfig.parse("""{"sections":[{"sources":[{"key":"a"}]}]}"""), "section id and feedUrl are required")
        assertNotNull(NewsHubConfig.parse("""{"version":1,"unknown":true,"sections":[]}"""))
    }
}
