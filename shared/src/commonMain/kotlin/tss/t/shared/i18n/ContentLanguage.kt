package tss.t.shared.i18n

/** A language the app is translated into (same list as Android `LanguagePrefs`). */
data class AppLanguage(
    /** BCP-47 tag of the UI (`pt-BR`). */
    val tag: String,
    /** ISO 639-1 code PodcastIndex understands in `lang` (`pt`). */
    val contentCode: String,
    /** Name in its own language. */
    val nativeName: String,
)

/** UI language of this app process (iOS: the app's own language setting, else the system's). */
expect fun appUiLanguageTag(): String

/** Region of the device (`VN`), or an empty string. */
expect fun systemRegionCode(): String

/**
 * First language of the device (`pt-PT`), whether or not the app is translated into it: what
 * the podcast location is detected from on first launch.
 */
expect fun systemLanguageTag(): String

/**
 * The 15 UI languages (owner, 2026-11-01). Which podcasts are listed is not decided here but by
 * the podcast locations ([tss.t.shared.location.PodcastLocationRepository]), as on Android.
 */
object ContentLanguages {
    val SUPPORTED: List<AppLanguage> = listOf(
        AppLanguage("vi", "vi", "Tiếng Việt"),
        AppLanguage("en", "en", "English"),
        AppLanguage("es", "es", "Español"),
        AppLanguage("pt-BR", "pt", "Português (Brasil)"),
        AppLanguage("fr", "fr", "Français"),
        AppLanguage("de", "de", "Deutsch"),
        AppLanguage("it", "it", "Italiano"),
        AppLanguage("nl", "nl", "Nederlands"),
        AppLanguage("sv", "sv", "Svenska"),
        AppLanguage("pl", "pl", "Polski"),
        AppLanguage("tr", "tr", "Türkçe"),
        AppLanguage("ja", "ja", "日本語"),
        AppLanguage("ko", "ko", "한국어"),
        AppLanguage("id", "id", "Bahasa Indonesia"),
        AppLanguage("hi", "hi", "हिन्दी"),
    )

    fun byTag(tag: String): AppLanguage? {
        val code = tag.substringBefore('-').substringBefore('_').lowercase().let { if (it == "in") "id" else it }
        return SUPPORTED.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: SUPPORTED.firstOrNull { it.contentCode == code }
    }

    /** Region VN → Vietnamese; else the UI language when supported; else English. */
    fun detect(languageTag: String, region: String): AppLanguage {
        if (region.equals("VN", ignoreCase = true)) return SUPPORTED.first { it.tag == "vi" }
        return byTag(languageTag) ?: SUPPORTED.first { it.tag == "en" }
    }
}
