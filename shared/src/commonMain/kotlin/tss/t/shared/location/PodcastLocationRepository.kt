package tss.t.shared.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.shared.i18n.systemLanguageTag
import tss.t.shared.i18n.systemRegionCode
import tss.t.shared.preferences.KeyValueStore

/**
 * The podcast locations (Android `PodcastLocationPrefs`, same key and value: `PodcastLocations` =
 * `VN,US`): up to [PodcastLocations.MAX_LOCATIONS] places in priority order.
 *
 * - First launch: the device location ([PodcastLocations.detect]: its region, else its language,
 *   else the US), saved at once.
 * - Afterwards: "Vị trí podcast" adds, removes and reorders them ([setLocations]).
 * - The first location is the primary one: Home lists use only its language ([contentLangParam],
 *   a single `lang`, so Vietnam lists Vietnamese shows only). Search keeps results in the
 *   languages of all chosen locations, ranked by priority ([contentCodes]).
 *
 * [locations] is empty until the first [current] call has read the store.
 */
class PodcastLocationRepository(
    private val store: KeyValueStore,
    private val deviceLanguage: () -> String = ::systemLanguageTag,
    private val deviceRegion: () -> String = ::systemRegionCode,
) {
    private val mutex = Mutex()
    private val _locations = MutableStateFlow<List<PodcastLocation>>(emptyList())

    /** The locations in priority order; empty until loaded. */
    val locations: StateFlow<List<PodcastLocation>> = _locations.asStateFlow()

    /** The saved locations (never empty), detecting and saving them on first launch. */
    suspend fun current(): List<PodcastLocation> = mutex.withLock { loadLocked() }

    suspend fun primary(): PodcastLocation = current().first()

    /** `lang` of the main Home lists (trending, "Có thể bạn yêu thích", recent): the primary only. */
    suspend fun contentLangParam(): String = primary().contentCode

    /** Languages search results may be in, primary first. */
    suspend fun contentCodes(): List<String> = current().map { it.contentCode }.distinct()

    /**
     * Saves [regions] in priority order (unknown ones dropped, deduplicated, at most
     * [PodcastLocations.MAX_LOCATIONS]) and returns what is saved. A list with no known region
     * changes nothing (there is always one location).
     */
    suspend fun setLocations(regions: List<String>): List<PodcastLocation> = mutex.withLock {
        val list = PodcastLocations.normalize(regions.mapNotNull(PodcastLocations::byRegion))
        if (list.isEmpty()) return@withLock loadLocked()
        store.putString(KEY_LOCATIONS, PodcastLocations.encode(list))
        _locations.value = list
        list
    }

    private suspend fun loadLocked(): List<PodcastLocation> {
        _locations.value.takeIf { it.isNotEmpty() }?.let { return it }
        val saved = PodcastLocations.decode(store.getString(KEY_LOCATIONS))
        val list = saved.ifEmpty {
            listOf(PodcastLocations.detect(deviceLanguage(), deviceRegion())).also {
                store.putString(KEY_LOCATIONS, PodcastLocations.encode(it))
            }
        }
        _locations.value = list
        return list
    }

    companion object {
        /** Android `PodcastLocationPrefs.KEY_LOCATIONS`. */
        const val KEY_LOCATIONS = "PodcastLocations"
    }
}

/** What a change of the locations reloads (Android `MainViewModel.init`). */
enum class LocationChange {
    /** Same list: nothing. */
    None,

    /** Another primary location: every Home list (they all use its language). */
    Primary,

    /** Reordered secondaries, or one added or removed: only the "by location" lists. */
    Secondary;

    companion object {
        fun between(previous: List<PodcastLocation>, current: List<PodcastLocation>): LocationChange = when {
            previous == current -> None
            previous.firstOrNull() != current.firstOrNull() -> Primary
            else -> Secondary
        }
    }
}
