package tss.t.shared.location

/**
 * A place podcasts are listed for (Android `tss.t.core.storage.PodcastLocation`). PodcastIndex has
 * no country field, so a location stands for the language its podcasts are published in: one
 * location per language ([contentCode]).
 *
 * Names are not here: each app names the country and the language in the UI language (iOS
 * `Locale.localizedString(forRegionCode:)`, Android `Locale.getDisplayCountry`).
 */
data class PodcastLocation(
    /** ISO 3166 region shown to the user (`VN`). */
    val region: String,
    /** ISO 639-1 code sent to PodcastIndex as `lang` (`vi`). */
    val contentCode: String,
) {
    /** Regional-indicator flag emoji for [region] (`VN` → 🇻🇳). */
    val flag: String
        get() = buildString {
            for (letter in region.uppercase()) {
                // U+1F1E6 + (letter - 'A'), written as its UTF-16 surrogate pair.
                val codePoint = 0x1F1E6 + (letter - 'A')
                val offset = codePoint - 0x10000
                append((0xD800 + (offset shr 10)).toChar())
                append((0xDC00 + (offset and 0x3FF)).toChar())
            }
        }
}

/**
 * The supported locations and the rules both apps follow (owner, 2026-11-01; Android
 * `PodcastLocationPrefs.Companion`): up to [MAX_LOCATIONS] places in priority order, one per
 * language; the first is the primary one.
 */
object PodcastLocations {
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

    /** The United States, the fallback of [detect]. */
    val DEFAULT: PodcastLocation = SUPPORTED.first { it.region == "US" }

    fun byRegion(region: String): PodcastLocation? =
        SUPPORTED.firstOrNull { it.region.equals(region.trim(), ignoreCase = true) }

    fun byContentCode(code: String): PodcastLocation? =
        SUPPORTED.firstOrNull { it.contentCode.equals(code.trim(), ignoreCase = true) }

    /**
     * Device locale → location: its [region] when listed, else its language ([languageTag],
     * `pt-PT` → Brazil, legacy `in` → Indonesia), else the United States.
     */
    fun detect(languageTag: String, region: String): PodcastLocation {
        byRegion(region)?.let { return it }
        return byContentCode(LanguageMatch.code(languageTag)) ?: DEFAULT
    }

    /** Deduplicated by region and by language, at most [MAX_LOCATIONS], order kept. */
    fun normalize(list: List<PodcastLocation>): List<PodcastLocation> =
        list.distinctBy { it.region }.distinctBy { it.contentCode }.take(MAX_LOCATIONS)

    /** Saved form: regions joined by commas (`VN,US`), the Android `PodcastLocations` value. */
    fun encode(list: List<PodcastLocation>): String = list.joinToString(",") { it.region }

    /** [encode] back; unknown regions are dropped, the result is [normalize]d (maybe empty). */
    fun decode(value: String?): List<PodcastLocation> =
        normalize(value.orEmpty().split(',').mapNotNull(::byRegion))
}
