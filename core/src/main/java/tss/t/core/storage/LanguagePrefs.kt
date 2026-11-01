package tss.t.core.storage

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A language the app is translated into and can load podcasts for. */
data class AppLanguage(
    /** BCP-47 tag used for the UI (`pt-BR`). */
    val tag: String,
    /** ISO 639-1 code PodcastIndex understands in `lang` (`pt`). */
    val contentCode: String,
    /** Name in its own language, for the picker. */
    val nativeName: String,
)

/**
 * App and content language (owner, 2026-11-01): the 15 largest podcast markets.
 *
 * - First launch: taken from the system language and region (a device set to region VN counts
 *   as Vietnamese), falling back to English, and saved.
 * - Afterwards: what the user picks in Settings → Ngôn ngữ (the UI language only).
 * - Which podcasts are listed is decided by [PodcastLocationPrefs] (2026-11-01): mixing English
 *   into every market ("vi,en") let English shows crowd Vietnamese ones out of trending.
 */
@Singleton
class LanguagePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPref: SharedPref,
) {
    private val _language = MutableStateFlow(loadOrDetect())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(tag: String) {
        val language = byTag(tag) ?: return
        sharedPref.save(KEY_APP_LANGUAGE, language.tag)
        _language.value = language
    }

    private fun loadOrDetect(): AppLanguage {
        sharedPref.get<String>(KEY_APP_LANGUAGE)?.let(::byTag)?.let { return it }
        val detected = detect(systemLocale())
        sharedPref.save(KEY_APP_LANGUAGE, detected.tag)
        return detected
    }

    private fun systemLocale(): Locale {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else @Suppress("DEPRECATION") config.locale
    }

    companion object {
        const val KEY_APP_LANGUAGE = "AppLanguage"

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

        fun byTag(tag: String): AppLanguage? =
            SUPPORTED.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: SUPPORTED.firstOrNull { it.contentCode.equals(tag.substringBefore('-'), ignoreCase = true) }

        /** System locale → supported language: region VN means Vietnamese, else the language, else English. */
        fun detect(locale: Locale): AppLanguage {
            if (locale.country.equals("VN", ignoreCase = true)) return SUPPORTED.first { it.tag == "vi" }
            val language = locale.language.lowercase().let { if (it == "in") "id" else it }
            return SUPPORTED.firstOrNull { it.contentCode == language } ?: SUPPORTED.first { it.tag == "en" }
        }
    }
}
