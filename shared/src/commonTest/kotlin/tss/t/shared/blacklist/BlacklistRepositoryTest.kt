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

    private fun distinctAssetValues(key: String): Int =
        Json.parseToJsonElement(BundledAssets.blacklist).jsonObject.getValue(key).jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()
            .size
}
