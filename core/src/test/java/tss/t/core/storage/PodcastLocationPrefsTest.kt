package tss.t.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PodcastLocationPrefsTest {
    @Test
    fun deviceRegionFirstThenLanguageThenUs() {
        assertEquals("VN", PodcastLocationPrefs.detect(Locale("en", "VN")).region)
        assertEquals("BR", PodcastLocationPrefs.detect(Locale("pt", "PT")).region)
        assertEquals("US", PodcastLocationPrefs.detect(Locale("en", "GB")).region)
        assertEquals("ID", PodcastLocationPrefs.detect(Locale("in", "XX")).region)
        assertEquals("US", PodcastLocationPrefs.detect(Locale("sw", "KE")).region)
    }

    @Test
    fun atMostThreeLocationsOnePerLanguage() {
        val list = listOf("VN", "US", "VN", "JP", "KR").mapNotNull(PodcastLocationPrefs::byRegion)
        assertEquals(listOf("VN", "US", "JP"), PodcastLocationPrefs.normalize(list).map { it.region })
    }

    @Test
    fun oneLocationPerLanguage() {
        val codes = PodcastLocationPrefs.SUPPORTED.map { it.contentCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun flagIsRegionalIndicators() {
        assertEquals("🇻🇳", PodcastLocationPrefs.byRegion("VN")!!.flag)
    }
}
