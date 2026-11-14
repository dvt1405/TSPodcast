package tss.t.shared.location

/**
 * Language helpers for location-based lists (Android `tss.t.podcasts.LanguageMatch`). PodcastIndex
 * tags a feed with whatever its RSS says (`vi`, `vi-VN`, `VI`), and spam feeds often claim a
 * language their title is not written in (Chinese SEO spam tagged `vi` showed up in Vietnamese
 * trending, 2026-11-01).
 */
object LanguageMatch {
    /** `vi-VN` → `vi`; `in` (legacy Indonesian) → `id`; null or blank → empty. */
    fun code(language: String?): String =
        language.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
            .let { if (it == "in") "id" else it }

    private val CJK_LANGUAGES = setOf("zh", "ja", "ko")

    /** False when [title] is written in Han / kana / hangul but [code] is not a CJK language. */
    fun titleFits(title: String?, code: String): Boolean {
        if (title.isNullOrBlank() || code in CJK_LANGUAGES) return true
        return title.none(::isCjk)
    }

    /** The Unicode blocks Android checks: CJK ideographs (+ extension A), hiragana, katakana, hangul. */
    private fun isCjk(c: Char): Boolean =
        c in '一'..'鿿' ||
            c in '㐀'..'䶿' ||
            c in '぀'..'ゟ' ||
            c in '゠'..'ヿ' ||
            c in '가'..'힯'
}
