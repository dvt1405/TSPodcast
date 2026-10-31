package tss.t.shared.newshub

import tss.t.shared.FakeConfig
import tss.t.shared.config.ConfigKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M6-AC14: Remote Config replaces the bundled file, broken values fall back, unconfirmed sources hide. */
class NewsHubConfigRepositoryTest {

    private fun remote(json: String?) = FakeConfig(mapOf(ConfigKeys.KEY_NEWS_HUB to json))

    private fun source(key: String, confirmedAt: String? = "2026-11-02", url: String = "https://$key.example.test/rss") =
        """{"key":"$key","feedUrl":"$url","confirmedAt":${confirmedAt?.let { "\"$it\"" } ?: "null"},"brief":{"take":1}}"""

    private fun hub(news: List<String>, night: List<String> = emptyList(), enabled: Boolean = true, version: Int = 1) =
        """{"version":$version,"enabled":$enabled,"sections":[
            {"id":"news","sources":[${news.joinToString(",")}]},
            {"id":"night","sleepTimerMinutes":30,"sources":[${night.joinToString(",")}]}]}"""

    @Test
    fun keyNameMatchesThePlan() {
        assertEquals("news_hub_v1", ConfigKeys.KEY_NEWS_HUB)
    }

    @Test
    fun validRemoteValueReplacesTheBundledFile() {
        val repository = NewsHubConfigRepository(remote(hub(listOf(source("a"), source("b")), listOf(source("n")))))

        assertTrue(repository.usesRemoteValue)
        assertTrue(repository.isEnabled)
        assertEquals(listOf("a", "b"), repository.config.newsSources.map { it.key })
        assertEquals(listOf("n"), repository.config.nightSources.map { it.key })
        assertEquals(30, repository.config.nightSleepTimerMinutes)
    }

    @Test
    fun brokenOrEmptyRemoteValueFallsBackToTheBundledFile() {
        listOf(null, "", "   ", "{not json", "[]", """{"sections":"x"}""", hub(listOf(source("a")), version = 2)).forEach { value ->
            val repository = NewsHubConfigRepository(remote(value), includeUnconfirmed = true)

            assertFalse(repository.usesRemoteValue, "value: $value")
            assertEquals(7, repository.config.newsSources.size, "value: $value")
            assertEquals("vov_thoisu", repository.config.newsSources.first().key)
        }
    }

    @Test
    fun bundledSourcesAreAllUnconfirmedSoTheHubIsOffByDefault() {
        val repository = NewsHubConfigRepository(FakeConfig())

        assertFalse(repository.isEnabled)
        assertEquals(NewsHubConfig.Disabled, repository.config)
    }

    @Test
    fun includeUnconfirmedKeepsEverySource() {
        val repository = NewsHubConfigRepository(FakeConfig(), includeUnconfirmed = true)

        assertTrue(repository.isEnabled)
        assertEquals(7, repository.config.newsSources.size)
        assertEquals(listOf("hanoi_doctruyen"), repository.config.nightSources.map { it.key })
    }

    @Test
    fun enabledFalseTurnsTheHubOff() {
        val repository = NewsHubConfigRepository(remote(hub(listOf(source("a")), enabled = false)))

        assertTrue(repository.usesRemoteValue, "a valid remote value is followed, even to turn the hub off")
        assertFalse(repository.isEnabled)
        assertTrue(repository.config.sections.isEmpty())
    }

    @Test
    fun unconfirmedSourcesAreDroppedAndDatedOnesKept() {
        val repository = NewsHubConfigRepository(
            remote(hub(listOf(source("a", confirmedAt = null), source("b"), source("c", confirmedAt = "soon")), listOf(source("n", null)))),
        )

        assertEquals(listOf("b"), repository.config.newsSources.map { it.key })
        assertTrue(repository.config.nightSources.isEmpty())
        assertEquals(listOf("news"), repository.config.sections.map { it.id }, "an emptied section is removed")
    }

    @Test
    fun noNewsSourceLeftTurnsTheHubOff() {
        val onlyNight = NewsHubConfigRepository(remote(hub(listOf(source("a", null)), listOf(source("n")))))
        val nothing = NewsHubConfigRepository(remote(hub(emptyList())))

        assertFalse(onlyNight.isEnabled)
        assertFalse(nothing.isEnabled)
    }

    @Test
    fun invalidAndDuplicateSourcesAreDropped() {
        val repository = NewsHubConfigRepository(
            remote(
                hub(
                    listOf(source("a"), source("a", url = "https://other.example.test/rss"), source(" "), source("b", url = "ftp://b.example.test/rss")),
                    listOf(source("a")),
                ),
            ),
        )

        assertEquals(listOf("a"), repository.config.newsSources.map { it.key })
        assertEquals("https://a.example.test/rss", repository.config.newsSources.single().feedUrl)
        assertTrue(repository.config.nightSources.isEmpty(), "a key is unique across sections")
    }

    @Test
    fun briefRulesAreNormalized() {
        val json = """{"sections":[{"id":"news","sources":[
            {"key":"a","feedUrl":"https://a.example.test/rss","confirmedAt":"2026-11-02","brief":{"take":0,"maxAgeHours":-5,"preferTitle":" "}}]},
            {"id":"night","sleepTimerMinutes":999,"sources":[{"key":"n","feedUrl":"https://n.example.test/rss","confirmedAt":"2026-11-02"}]}]}"""
        val config = NewsHubConfigRepository(remote(json)).config

        assertEquals(NewsBriefRule(take = 1, maxAgeHours = 24), config.newsSources.single().brief)
        assertEquals(180, config.nightSleepTimerMinutes)
    }

    @Test
    fun reloadReadsRemoteConfigAgain() {
        val config = remote(null)
        val repository = NewsHubConfigRepository(config)
        assertFalse(repository.isEnabled)

        config.values = mapOf(ConfigKeys.KEY_NEWS_HUB to hub(listOf(source("a"))))
        assertFalse(repository.isEnabled, "nothing changes before reload()")
        repository.reload()

        assertTrue(repository.isEnabled)
        assertEquals(listOf("a"), repository.config.newsSources.map { it.key })
    }
}
