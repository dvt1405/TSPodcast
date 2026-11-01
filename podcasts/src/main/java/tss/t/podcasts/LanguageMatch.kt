package tss.t.podcasts

/**
 * Language helpers for location-based lists. PodcastIndex tags a feed with whatever its RSS says
 * (`vi`, `vi-VN`, `VI`), and spam feeds often claim a language their title is not written in
 * (Chinese SEO spam tagged `vi` showed up in Vietnamese trending, 2026-11-01).
 */
object LanguageMatch {
    /** `vi-VN` → `vi`; `in` (legacy Indonesian) → `id`; blank → empty. */
    fun code(language: String?): String =
        language.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
            .let { if (it == "in") "id" else it }

    private val CJK_LANGUAGES = setOf("zh", "ja", "ko")

    /** False when [title] is written in Han / kana / hangul but [code] is not a CJK language. */
    fun titleFits(title: String?, code: String): Boolean {
        if (title.isNullOrBlank() || code in CJK_LANGUAGES) return true
        return title.none(::isCjk)
    }

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
