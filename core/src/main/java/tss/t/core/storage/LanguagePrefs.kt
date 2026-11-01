package tss.t.core.storage

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * App (UI) language, 15 languages (owner, 2026-11-01).
 *
 * - "Theo khu vực" ([followRegion], owner 2026-11-02): the UI language follows the main podcast
 *   location ([PodcastLocationPrefs.primary]), e.g. Vietnam → Tiếng Việt, Brazil → Português.
 *   Locations without a UI translation (China, Thailand, Russia) use the system language. This is
 *   the default for new installs and for users who never picked a language themselves.
 * - Picking a language in Settings → Ngôn ngữ pins it ([setLanguage]) until "Theo khu vực" is
 *   chosen again.
 * - [KEY_APP_LANGUAGE] always holds the language in effect, so [tss.t.podcast.AppLocale] can read
 *   it before Hilt on Android < 13.
 * - Which podcasts are listed is decided by [PodcastLocationPrefs] alone.
 */
@Singleton
class LanguagePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPref: SharedPref,
    private val locationPrefs: PodcastLocationPrefs,
) {
    private val _followRegion = MutableStateFlow(loadFollowRegion())
    val followRegion: StateFlow<Boolean> = _followRegion.asStateFlow()

    private val _language = MutableStateFlow(resolve())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        persist(_language.value)
        // A new main location changes the UI language while following the region.
        scope.launch {
            locationPrefs.locations.collect { if (_followRegion.value) update(resolve()) }
        }
    }

    /** Pins [tag] as the UI language (stops following the region). */
    fun setLanguage(tag: String) {
        val language = byTag(tag) ?: return
        setFollow(false)
        update(language)
    }

    /** UI language follows the main podcast location again. */
    fun followRegion() {
        setFollow(true)
        update(resolve())
    }

    /** The language "Theo khu vực" gives right now (for the picker's label). */
    fun regionLanguage(): AppLanguage =
        byTag(locationPrefs.primary.contentCode) ?: detect(systemLocale())

    private fun resolve(): AppLanguage =
        if (_followRegion.value) {
            regionLanguage()
        } else {
            sharedPref.get<String>(KEY_APP_LANGUAGE)?.let(::byTag) ?: detect(systemLocale())
        }

    private fun update(language: AppLanguage) {
        persist(language)
        _language.value = language
    }

    private fun persist(language: AppLanguage) {
        if (sharedPref.get<String>(KEY_APP_LANGUAGE) != language.tag) sharedPref.save(KEY_APP_LANGUAGE, language.tag)
    }

    private fun setFollow(follow: Boolean) {
        sharedPref.save(KEY_FOLLOW_REGION, follow)
        _followRegion.value = follow
    }

    /**
     * Saved choice, else: a new install follows the region; an older install follows it only if
     * its saved language is still the one detected from the system (never picked by hand).
     */
    private fun loadFollowRegion(): Boolean {
        sharedPref.get<Boolean>(KEY_FOLLOW_REGION)?.let { return it }
        val saved = sharedPref.get<String>(KEY_APP_LANGUAGE)?.let(::byTag)
        val follow = saved == null || saved.tag == detect(systemLocale()).tag
        sharedPref.save(KEY_FOLLOW_REGION, follow)
        return follow
    }

    private fun systemLocale(): Locale {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else @Suppress("DEPRECATION") config.locale
    }

    companion object {
        const val KEY_APP_LANGUAGE = "AppLanguage"
        const val KEY_FOLLOW_REGION = "AppLanguageFollowsRegion"

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
