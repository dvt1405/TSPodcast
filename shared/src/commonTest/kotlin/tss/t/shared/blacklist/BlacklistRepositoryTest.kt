package tss.t.shared.blacklist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.t.shared.FakeConfig
import tss.t.shared.config.ConfigKeys
import tss.t.shared.generated.BundledAssets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlacklistRepositoryTest {

    private val override = """{"feedId":["1"],"filterText":["abc"]}"""

    @Test
    fun noRemoteValueUsesTheBundledAsset() {
        val repository = BlacklistRepository(FakeConfig.blacklist(null))

        assertTrue(repository.isInBlacklist("7101342"))
        assertTrue(repository.isInBlacklist("6960122"))
        assertFalse(repository.isInBlacklist("1"))
        // The asset lists 21 ids with duplicates; Android stores them in a Set.
        assertEquals(19, repository.feedIdCount)
        assertEquals(10, repository.keywordCount)
        assertEquals(distinctAssetValues("feedId"), repository.feedIdCount)
        assertEquals(distinctAssetValues("filterText"), repository.keywordCount)
    }

    @Test
    fun missingKeyUsesTheBundledAsset() {
        val repository = BlacklistRepository(FakeConfig())

        assertTrue(repository.isInBlacklist("7101342"))
        assertEquals(19, repository.feedIdCount)
    }

    @Test
    fun remoteValueReplacesTheAsset() {
        val repository = BlacklistRepository(FakeConfig.blacklist(override))

        assertTrue(repository.isInBlacklist("1"))
        assertFalse(repository.isInBlacklist("7101342"))
        assertTrue(repository.isContainKeywordsBlacklist("xabcx"))
        assertFalse(repository.isContainKeywordsBlacklist("casino"))
        assertEquals(1, repository.feedIdCount)
        assertEquals(1, repository.keywordCount)
    }

    @Test
    fun remoteValueThatIsNotAJsonObjectFallsBackToTheAsset() {
        listOf("not json", "", "   ", "[]", "\"text\"", "{broken").forEach { value ->
            val repository = BlacklistRepository(FakeConfig.blacklist(value))
            assertTrue(repository.isInBlacklist("7101342"), "value <$value> should fall back")
            assertEquals(10, repository.keywordCount, "value <$value> should fall back")
        }
    }

    @Test
    fun remoteObjectWithoutListsBlocksNothing() {
        // Android: getJSONObject("{}") is non-null, so the asset is not read either.
        val repository = BlacklistRepository(FakeConfig.blacklist("{}"))

        assertFalse(repository.isInBlacklist("7101342"))
        assertFalse(repository.isContainKeywordsBlacklist("casino"))
    }

    @Test
    fun keywordMatchingIgnoresCase() {
        val repository = BlacklistRepository(FakeConfig.blacklist(override))

        assertTrue(repository.isContainKeywordsBlacklist("xxABCxx"))
        assertTrue(repository.isContainKeywordsBlacklist("Abc"))
        assertFalse(repository.isContainKeywordsBlacklist("ab c"))
    }

    @Test
    fun uppercaseRemoteKeywordStillMatches() {
        val repository = BlacklistRepository(FakeConfig.blacklist("""{"filterText":["CASINO"]}"""))

        assertTrue(repository.isContainKeywordsBlacklist("best casino"))
    }

    @Test
    fun vietnameseAssetKeywordsMatchAnyCase() {
        val repository = BlacklistRepository(FakeConfig())

        assertTrue(repository.isContainKeywordsBlacklist("Kèo CÁ CƯỢC hôm nay"))
        assertTrue(repository.isContainKeywordsBlacklist("Top Nhà Cái uy tín"))
        assertFalse(repository.isContainKeywordsBlacklist("Tin tức buổi sáng"))
    }

    @Test
    fun nullOrEmptyTextIsNeverBlacklisted() {
        val repository = BlacklistRepository(FakeConfig.blacklist(override))

        assertFalse(repository.isContainKeywordsBlacklist(null))
        assertFalse(repository.isContainKeywordsBlacklist(""))
        assertFalse(repository.isContainKeywordsBlacklist(null, filterSpace = true))
    }

    @Test
    fun filterSpaceReallyRemovesSpaces() {
        val repository = BlacklistRepository(FakeConfig.blacklist(override))

        assertTrue(repository.isContainKeywordsBlacklist("a b c", filterSpace = true))
        assertTrue(repository.isContainKeywordsBlacklist("A\tB\nC", filterSpace = true))
        assertFalse(repository.isContainKeywordsBlacklist("a b c", filterSpace = false))
        assertFalse(repository.isContainKeywordsBlacklist("a b c"))
    }

    @Test
    fun filterSpaceAlsoMatchesKeywordsThatContainSpaces() {
        val repository = BlacklistRepository(FakeConfig())

        assertTrue(repository.isContainKeywordsBlacklist("cácược", filterSpace = true))
        assertFalse(repository.isContainKeywordsBlacklist("cácược"))
    }

    @Test
    fun blankEntriesAreIgnoredAndNumbersAreKeptAsText() {
        val repository = BlacklistRepository(
            FakeConfig.blacklist("""{"feedId":[123, "", null, " 456 "],"filterText":["", "  "]}""")
        )

        assertTrue(repository.isInBlacklist("123"))
        assertTrue(repository.isInBlacklist("456"))
        assertFalse(repository.isInBlacklist(""))
        assertEquals(0, repository.keywordCount)
        assertFalse(repository.isContainKeywordsBlacklist("anything"))
    }

    @Test
    fun reloadReadsTheConfigAgain() {
        val config = FakeConfig()
        val repository = BlacklistRepository(config)
        assertTrue(repository.isInBlacklist("7101342"))

        config.values = mapOf(ConfigKeys.KEY_BLACK_LIST to override)
        assertTrue(repository.isInBlacklist("7101342"), "no change before reload")
        repository.reload()

        assertFalse(repository.isInBlacklist("7101342"))
        assertTrue(repository.isInBlacklist("1"))
    }

    // M6-AC10: host and author lists (N1 lite).

    @Test
    fun bundledExtraBlocksNgocbaomediaAndItsSubdomains() {
        val repository = BlacklistRepository(FakeConfig())

        assertTrue(repository.isBlockedHost("https://ngocbaomedia.com/feed/x.xml"))
        assertTrue(repository.isBlockedHost("https://www.ngocbaomedia.com/podcast/1"))
        assertTrue(repository.isBlockedHost("https://abc.ngocbaomedia.com/a.mp3"))
        assertTrue(repository.isBlockedHost("HTTP://Media.NgocBaoMedia.com:8080/a.mp3?x=1"))
        assertTrue(repository.isBlockedHost("https://user@ngocbaomedia.com./feed"))
        assertFalse(repository.isBlockedHost("https://notngocbaomedia.com/feed"))
        assertFalse(repository.isBlockedHost("https://ngocbaomedia.com.example.org/feed"))
        assertFalse(repository.isBlockedHost("https://example.org/ngocbaomedia.com"))
        assertFalse(repository.isBlockedHost("not a url"))
        assertFalse(repository.isBlockedHost("ngocbaomedia.com/feed"))
        assertFalse(repository.isBlockedHost(""))
        assertFalse(repository.isBlockedHost(null))
        assertEquals(1, repository.hostCount)
        assertEquals(0, repository.authorCount)
    }

    @Test
    fun remoteWithoutHostOrAuthorKeysKeepsTheBundledExtra() {
        // The UAT value today: only feedId/filterText.
        listOf(override, "{}", "not json").forEach { value ->
            val repository = BlacklistRepository(FakeConfig.blacklist(value))
            assertTrue(repository.isBlockedHost("https://ngocbaomedia.com/feed"), "value <$value>")
        }
    }

    @Test
    fun remoteHostListReplacesTheBundledOne() {
        val repository = BlacklistRepository(
            FakeConfig.blacklist("""{"feedId":[],"host":["Spam.example", "*.junk.test", "https://www.bad.test/feed", " ", 7]}""")
        )

        assertTrue(repository.isBlockedHost("https://spam.example/a"))
        assertTrue(repository.isBlockedHost("https://cdn.spam.example/a.mp3"))
        assertTrue(repository.isBlockedHost("https://x.junk.test/a"))
        assertTrue(repository.isBlockedHost("https://junk.test/a"))
        assertTrue(repository.isBlockedHost("https://bad.test/rss"))
        assertFalse(repository.isBlockedHost("https://ngocbaomedia.com/feed"), "the remote list replaced the bundled one")
        assertEquals(4, repository.hostCount, "blank dropped, the number kept as text")
    }

    @Test
    fun emptyRemoteHostListUnblocksEveryHost() {
        val repository = BlacklistRepository(FakeConfig.blacklist("""{"host":[]}"""))

        assertFalse(repository.isBlockedHost("https://ngocbaomedia.com/feed"))
        assertEquals(0, repository.hostCount)
    }

    @Test
    fun authorsMatchWholeNamesIgnoringCaseAndSpaces() {
        val repository = BlacklistRepository(FakeConfig.blacklist("""{"author":["Ngọc Bảo Media", ""]}"""))

        assertTrue(repository.isBlockedAuthor("ngọc bảo media"))
        assertTrue(repository.isBlockedAuthor("  NGỌC BẢO MEDIA "))
        assertFalse(repository.isBlockedAuthor("Ngọc Bảo Media Official"), "no substring match")
        assertFalse(repository.isBlockedAuthor("Bảo"))
        assertFalse(repository.isBlockedAuthor(""))
        assertFalse(repository.isBlockedAuthor(null))
        assertEquals(1, repository.authorCount)
        assertTrue(repository.isBlockedHost("https://ngocbaomedia.com/feed"), "no host key: bundled hosts")
    }

    @Test
    fun reloadReadsTheHostListAgain() {
        val config = FakeConfig()
        val repository = BlacklistRepository(config)
        config.values = mapOf(ConfigKeys.KEY_BLACK_LIST to """{"host":["other.test"]}""")

        repository.reload()

        assertFalse(repository.isBlockedHost("https://ngocbaomedia.com/feed"))
        assertTrue(repository.isBlockedHost("https://other.test/feed"))
    }

    @Test
    fun bundledExtraFileHasTheSeedHostAndNoAuthor() {
        val extra = Json.parseToJsonElement(BundledAssets.blacklistExtra).jsonObject

        assertEquals(listOf("ngocbaomedia.com"), extra.getValue("host").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(0, extra.getValue("author").jsonArray.size)
    }

    private fun distinctAssetValues(key: String): Int =
        Json.parseToJsonElement(BundledAssets.blacklist).jsonObject.getValue(key).jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()
            .size
}
