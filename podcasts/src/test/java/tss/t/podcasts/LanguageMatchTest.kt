package tss.t.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageMatchTest {
    @Test
    fun normalisesFeedLanguageTags() {
        assertEquals("vi", LanguageMatch.code("vi-VN"))
        assertEquals("vi", LanguageMatch.code(" VI "))
        assertEquals("en", LanguageMatch.code("en_us"))
        assertEquals("id", LanguageMatch.code("in"))
        assertEquals("", LanguageMatch.code(null))
    }

    @Test
    fun rejectsHanTitlesClaimingVietnamese() {
        assertFalse(LanguageMatch.titleFits("公众号均速阅读模拟提升指南推荐这个下单官方 BLfen.top", "vi"))
        assertTrue(LanguageMatch.titleFits("Tạp chí Việt Nam", "vi"))
        assertTrue(LanguageMatch.titleFits("日本のポッドキャスト", "ja"))
    }
}
