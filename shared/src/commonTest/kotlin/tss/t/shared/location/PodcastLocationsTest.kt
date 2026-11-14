package tss.t.shared.location

import kotlinx.coroutines.test.runTest
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The location rules of Android `PodcastLocationPrefs` (and its `PodcastLocationPrefsTest`). */
class PodcastLocationsTest {

    @Test
    fun deviceRegionFirstThenLanguageThenUs() {
        assertEquals("VN", PodcastLocations.detect("en-VN", "VN").region)
        assertEquals("BR", PodcastLocations.detect("pt-PT", "PT").region)
        assertEquals("US", PodcastLocations.detect("en-GB", "GB").region)
        assertEquals("ID", PodcastLocations.detect("in", "XX").region)
        assertEquals("JP", PodcastLocations.detect("ja-JP", "").region)
        assertEquals("US", PodcastLocations.detect("sw-KE", "KE").region)
        assertEquals("US", PodcastLocations.detect("", "").region)
        assertEquals("VN", PodcastLocations.detect("fr-FR", "vn").region, "region is case-insensitive")
    }

    @Test
    fun eighteenLocationsOnePerLanguageVietnamFirst() {
        assertEquals(18, PodcastLocations.SUPPORTED.size)
        assertEquals("VN", PodcastLocations.SUPPORTED.first().region)
        val codes = PodcastLocations.SUPPORTED.map { it.contentCode }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(
            listOf("vi", "en", "es", "pt", "fr", "de", "it", "nl", "sv", "pl", "tr", "ja", "ko", "zh", "id", "th", "hi", "ru"),
            codes,
        )
    }

    @Test
    fun atMostThreeLocationsOnePerLanguage() {
        val list = listOf("VN", "US", "VN", "JP", "KR").mapNotNull(PodcastLocations::byRegion)
        assertEquals(listOf("VN", "US", "JP"), PodcastLocations.normalize(list).map { it.region })
    }

    @Test
    fun flagIsRegionalIndicators() {
        assertEquals("🇻🇳", PodcastLocations.byRegion("VN")!!.flag)
        assertEquals("🇺🇸", PodcastLocations.byRegion("us")!!.flag)
        assertEquals("🇧🇷", PodcastLocations.byRegion("BR")!!.flag)
    }

    @Test
    fun savedValueIsTheAndroidOne() {
        val list = listOf("VN", "US").mapNotNull(PodcastLocations::byRegion)
        assertEquals("VN,US", PodcastLocations.encode(list))
        assertEquals(list, PodcastLocations.decode("VN,US"))
        assertEquals(listOf("JP"), PodcastLocations.decode(" jp ,XX,").map { it.region })
        assertEquals(emptyList(), PodcastLocations.decode(null))
        assertEquals(emptyList(), PodcastLocations.decode(""))
    }

    @Test
    fun locationChangeDecidesWhatReloads() {
        val vn = PodcastLocations.byRegion("VN")!!
        val us = PodcastLocations.byRegion("US")!!
        val jp = PodcastLocations.byRegion("JP")!!
        assertEquals(LocationChange.None, LocationChange.between(listOf(vn, us), listOf(vn, us)))
        assertEquals(LocationChange.Primary, LocationChange.between(listOf(vn, us), listOf(us, vn)))
        assertEquals(LocationChange.Primary, LocationChange.between(listOf(vn), listOf(jp)))
        assertEquals(LocationChange.Secondary, LocationChange.between(listOf(vn, us), listOf(vn, us, jp)))
        assertEquals(LocationChange.Secondary, LocationChange.between(listOf(vn, us, jp), listOf(vn, jp, us)))
        assertEquals(LocationChange.Secondary, LocationChange.between(listOf(vn, us), listOf(vn)))
    }
}

class PodcastLocationRepositoryTest {

    private fun repository(
        store: InMemoryKeyValueStore = InMemoryKeyValueStore(),
        language: String = "en-US",
        region: String = "VN",
    ) = PodcastLocationRepository(store, { language }, { region })

    @Test
    fun firstLaunchDetectsAndSavesTheDeviceLocation() = runTest {
        val store = InMemoryKeyValueStore()
        val repository = repository(store, language = "pt-PT", region = "PT")
        assertEquals(emptyList(), repository.locations.value, "empty until loaded")

        assertEquals(listOf("BR"), repository.current().map { it.region })
        assertEquals("BR", store.snapshot()[PodcastLocationRepository.KEY_LOCATIONS])
        assertEquals(listOf("BR"), repository.locations.value.map { it.region })
        assertEquals("pt", repository.contentLangParam())
    }

    @Test
    fun savedLocationsWinOverTheDevice() = runTest {
        val store = InMemoryKeyValueStore(mapOf(PodcastLocationRepository.KEY_LOCATIONS to "JP,VN"))
        val repository = repository(store, region = "US")

        assertEquals(listOf("JP", "VN"), repository.current().map { it.region })
        assertEquals("ja", repository.contentLangParam())
        assertEquals(listOf("ja", "vi"), repository.contentCodes())
    }

    @Test
    fun anUnreadableSavedValueIsDetectedAgain() = runTest {
        val store = InMemoryKeyValueStore(mapOf(PodcastLocationRepository.KEY_LOCATIONS to "XX,,"))

        assertEquals(listOf("VN"), repository(store).current().map { it.region })
        assertEquals("VN", store.snapshot()[PodcastLocationRepository.KEY_LOCATIONS])
    }

    @Test
    fun setLocationsNormalizesSavesAndPublishes() = runTest {
        val store = InMemoryKeyValueStore()
        val repository = repository(store)
        repository.current()

        val saved = repository.setLocations(listOf("us", "VN", "US", "XX", "JP", "KR"))

        assertEquals(listOf("US", "VN", "JP"), saved.map { it.region })
        assertEquals("US,VN,JP", store.snapshot()[PodcastLocationRepository.KEY_LOCATIONS])
        assertEquals(saved, repository.locations.value)
        assertEquals("en", repository.contentLangParam(), "Home lists use the primary language only")
        assertEquals(listOf("en", "vi", "ja"), repository.contentCodes())

        // A relaunch on the same store reads them back.
        assertEquals(saved, repository(store, region = "FR").current())
    }

    @Test
    fun anEmptyListChangesNothing() = runTest {
        val store = InMemoryKeyValueStore(mapOf(PodcastLocationRepository.KEY_LOCATIONS to "VN,US"))
        val repository = repository(store)

        assertEquals(listOf("VN", "US"), repository.setLocations(emptyList()).map { it.region })
        assertEquals(listOf("VN", "US"), repository.setLocations(listOf("XX")).map { it.region })
        assertEquals("VN,US", store.snapshot()[PodcastLocationRepository.KEY_LOCATIONS])
    }

    @Test
    fun setLocationsBeforeLoadingStillSaves() = runTest {
        val store = InMemoryKeyValueStore()
        val repository = repository(store)

        repository.setLocations(listOf("DE"))

        assertEquals(listOf("DE"), repository.current().map { it.region })
        assertFalse(store.snapshot().isEmpty())
        assertTrue(repository.locations.value.isNotEmpty())
    }
}

/** Android `LanguageMatchTest`, plus the other scripts the filter checks. */
class LanguageMatchTest {
    @Test
    fun normalisesFeedLanguageTags() {
        assertEquals("vi", LanguageMatch.code("vi-VN"))
        assertEquals("vi", LanguageMatch.code(" VI "))
        assertEquals("en", LanguageMatch.code("en_us"))
        assertEquals("id", LanguageMatch.code("in"))
        assertEquals("", LanguageMatch.code(null))
        assertEquals("", LanguageMatch.code("  "))
    }

    @Test
    fun rejectsHanTitlesClaimingVietnamese() {
        assertFalse(LanguageMatch.titleFits("公众号均速阅读模拟提升指南推荐这个下单官方 BLfen.top", "vi"))
        assertTrue(LanguageMatch.titleFits("Tạp chí Việt Nam", "vi"))
        assertTrue(LanguageMatch.titleFits("日本のポッドキャスト", "ja"))
    }

    @Test
    fun checksKanaHangulAndBlankTitles() {
        assertFalse(LanguageMatch.titleFits("カタカナ show", "en"))
        assertFalse(LanguageMatch.titleFits("ひらがな", "fr"))
        assertFalse(LanguageMatch.titleFits("한국어 팟캐스트", "en"))
        assertTrue(LanguageMatch.titleFits("한국어 팟캐스트", "ko"))
        assertTrue(LanguageMatch.titleFits("中文节目", "zh"))
        assertTrue(LanguageMatch.titleFits("Русский подкаст", "en"), "only CJK scripts are checked")
        assertTrue(LanguageMatch.titleFits("हिन्दी", "en"))
        assertTrue(LanguageMatch.titleFits(null, "vi"))
        assertTrue(LanguageMatch.titleFits(" ", "vi"))
    }
}
