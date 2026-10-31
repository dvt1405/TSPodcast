package tss.t.shared.i18n

import tss.t.shared.preferences.KeyValueStore

/** A language the app is translated into and loads podcasts in (same list as Android `LanguagePrefs`). */
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
 * The 15 languages (owner, 2026-11-01) and the rules shared by Android and iOS:
 * region VN → Vietnamese; else the UI language when supported; else English. The `lang` sent to
 * PodcastIndex lists always adds English for other languages ("vi,en").
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

    fun detect(languageTag: String, region: String): AppLanguage {
        if (region.equals("VN", ignoreCase = true)) return SUPPORTED.first { it.tag == "vi" }
        return byTag(languageTag) ?: SUPPORTED.first { it.tag == "en" }
    }

    fun langParam(language: AppLanguage): String =
        if (language.contentCode == "en") "en" else "${language.contentCode},en"
}

/**
 * Content language: detected from the system on first launch and saved; afterwards it follows the
 * app language setting (on iOS the app's own language in system Settings, read at each launch, or
 * [set] from the app), so podcasts are loaded in the language the user reads the app in.
 */
class ContentLanguageRepository(private val store: KeyValueStore) {

    suspend fun current(): AppLanguage {
        val ui = appUiLanguageTag()
        val saved = store.getString(KEY_LANGUAGE)?.let(ContentLanguages::byTag)
        val savedUi = store.getString(KEY_UI_LANGUAGE)
        val language = when {
            saved == null -> ContentLanguages.detect(ui, systemRegionCode())
            savedUi != null && !savedUi.equals(ui, ignoreCase = true) -> ContentLanguages.byTag(ui) ?: saved
            else -> saved
        }
        if (saved != language) store.putString(KEY_LANGUAGE, language.tag)
        if (savedUi != ui) store.putString(KEY_UI_LANGUAGE, ui)
        return language
    }

    suspend fun set(tag: String) {
        val language = ContentLanguages.byTag(tag) ?: return
        store.putString(KEY_LANGUAGE, language.tag)
    }

    suspend fun langParam(): String = ContentLanguages.langParam(current())

    /** Search results ranking: the chosen language first, then English. */
    suspend fun preferredCodes(): List<String> = listOf(current().contentCode, "en").distinct()

    companion object {
        const val KEY_LANGUAGE = "content.language"
        const val KEY_UI_LANGUAGE = "content.language.ui"
    }
}
