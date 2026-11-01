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

/**
 * A place podcasts are listed for. PodcastIndex has no country field, so a location stands for
 * the language its podcasts are published in: one location per language ([contentCode]).
 */
data class PodcastLocation(
    /** ISO 3166 region shown to the user (`VN`). */
    val region: String,
    /** ISO 639-1 code sent to PodcastIndex as `lang` (`vi`). */
    val contentCode: String,
) {
    /** Regional-indicator flag emoji for [region]. */
    val flag: String
        get() = region.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

    /** Country name in [displayLocale] (`Việt Nam`, `Vietnam`). */
    fun displayName(displayLocale: Locale = Locale.getDefault()): String =
        Locale("", region).getDisplayCountry(displayLocale).ifBlank { region }

    /** Language name in its own language (`Tiếng Việt`, `English`). */
    fun languageName(): String {
        val locale = Locale(contentCode)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

/**
 * Podcast locations (owner, 2026-11-01): up to [MAX_LOCATIONS] places, in priority order.
 *
 * - First launch: the device location (its region, else its language), saved.
 * - Afterwards: Settings / Home → "Vị trí podcast" adds, removes and reorders them.
 * - The first location is the primary one: Home lists and search use only its language
 *   ([contentLangParam]), so Vietnam shows Vietnamese shows only. Search keeps results in the
 *   languages of all chosen locations, ranked by priority ([contentCodes]).
 * - Home also shows one "by location" list per location.
 */
@Singleton
class PodcastLocationPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPref: SharedPref,
) {
    private val _locations = MutableStateFlow(loadOrDetect())
    val locations: StateFlow<List<PodcastLocation>> = _locations.asStateFlow()

    val primary: PodcastLocation get() = _locations.value.first()

    /** `lang` for the main Home lists (trending, recent, news): the primary location only. */
    fun contentLangParam(): String = primary.contentCode

    /** Languages search results may be in, primary first. */
    fun contentCodes(): List<String> = _locations.value.map { it.contentCode }.distinct()

    /** Saves [regions] (deduplicated, at most [MAX_LOCATIONS], never empty) in priority order. */
    fun setLocations(regions: List<String>) {
        val list = normalize(regions.mapNotNull(::byRegion))
        if (list.isEmpty()) return
        sharedPref.save(KEY_LOCATIONS, list.joinToString(",") { it.region })
        _locations.value = list
    }

    private fun loadOrDetect(): List<PodcastLocation> {
        sharedPref.get<String>(KEY_LOCATIONS)
            ?.split(',')
            ?.mapNotNull(::byRegion)
            ?.let(::normalize)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val detected = listOf(detect(systemLocale()))
        sharedPref.save(KEY_LOCATIONS, detected.joinToString(",") { it.region })
        return detected
    }

    private fun systemLocale(): Locale {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else @Suppress("DEPRECATION") config.locale
    }

    companion object {
        const val KEY_LOCATIONS = "PodcastLocations"
        const val MAX_LOCATIONS = 3

        /** One location per language, ordered by podcast market size (Vietnam first for this app). */
        val SUPPORTED: List<PodcastLocation> = listOf(
            PodcastLocation("VN", "vi"),
            PodcastLocation("US", "en"),
            PodcastLocation("ES", "es"),
            PodcastLocation("BR", "pt"),
            PodcastLocation("FR", "fr"),
            PodcastLocation("DE", "de"),
            PodcastLocation("IT", "it"),
            PodcastLocation("NL", "nl"),
            PodcastLocation("SE", "sv"),
            PodcastLocation("PL", "pl"),
            PodcastLocation("TR", "tr"),
            PodcastLocation("JP", "ja"),
            PodcastLocation("KR", "ko"),
            PodcastLocation("CN", "zh"),
            PodcastLocation("ID", "id"),
            PodcastLocation("TH", "th"),
            PodcastLocation("IN", "hi"),
            PodcastLocation("RU", "ru"),
        )

        fun byRegion(region: String): PodcastLocation? =
            SUPPORTED.firstOrNull { it.region.equals(region.trim(), ignoreCase = true) }

        fun byContentCode(code: String): PodcastLocation? =
            SUPPORTED.firstOrNull { it.contentCode.equals(code, ignoreCase = true) }

        /** Device locale → location: its region when listed, else its language, else the US. */
        fun detect(locale: Locale): PodcastLocation {
            byRegion(locale.country)?.let { return it }
            val language = locale.language.lowercase().let { if (it == "in") "id" else it }
            return byContentCode(language) ?: SUPPORTED.first { it.region == "US" }
        }

        /** Deduplicated by region and by language, at most [MAX_LOCATIONS]. */
        fun normalize(list: List<PodcastLocation>): List<PodcastLocation> =
            list.distinctBy { it.region }.distinctBy { it.contentCode }.take(MAX_LOCATIONS)
    }
}
