package tss.t.shared.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentLanguagesTest {
    @Test
    fun regionVietnamMeansVietnamese() {
        assertEquals("vi", ContentLanguages.detect("en-US", "VN").tag)
    }

    @Test
    fun supportedLanguageElseEnglish() {
        assertEquals("pt-BR", ContentLanguages.detect("pt-PT", "PT").tag)
        assertEquals("ko", ContentLanguages.detect("ko-KR", "KR").tag)
        assertEquals("id", ContentLanguages.detect("in", "ID").tag)
        assertEquals("en", ContentLanguages.detect("ru-RU", "RU").tag)
    }

    @Test
    fun englishIsAlwaysAdded() {
        assertEquals("vi,en", ContentLanguages.langParam(ContentLanguages.byTag("vi")!!))
        assertEquals("ja,en", ContentLanguages.langParam(ContentLanguages.byTag("ja-JP")!!))
        assertEquals("en", ContentLanguages.langParam(ContentLanguages.byTag("en-GB")!!))
    }

    @Test
    fun sameFifteenLanguagesAsAndroid() {
        assertEquals(15, ContentLanguages.SUPPORTED.size)
    }
}
