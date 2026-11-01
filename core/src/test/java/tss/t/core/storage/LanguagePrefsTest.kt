package tss.t.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguagePrefsTest {
    @Test
    fun regionVietnamMeansVietnameseWhateverTheSystemLanguage() {
        assertEquals("vi", LanguagePrefs.detect(Locale("en", "VN")).tag)
        assertEquals("vi", LanguagePrefs.detect(Locale("vi", "VN")).tag)
    }

    @Test
    fun systemLanguageIsUsedWhenSupportedElseEnglish() {
        assertEquals("pt-BR", LanguagePrefs.detect(Locale("pt", "BR")).tag)
        assertEquals("pt-BR", LanguagePrefs.detect(Locale("pt", "PT")).tag)
        assertEquals("ja", LanguagePrefs.detect(Locale.JAPAN).tag)
        assertEquals("id", LanguagePrefs.detect(Locale("in", "ID")).tag) // legacy Java code for Indonesian
        assertEquals("en", LanguagePrefs.detect(Locale("ru", "RU")).tag)
        assertEquals("en", LanguagePrefs.detect(Locale.US).tag)
    }

    @Test
    fun fifteenLanguagesWithUniqueTags() {
        assertEquals(15, LanguagePrefs.SUPPORTED.size)
        assertEquals(15, LanguagePrefs.SUPPORTED.map { it.tag }.toSet().size)
    }
}
