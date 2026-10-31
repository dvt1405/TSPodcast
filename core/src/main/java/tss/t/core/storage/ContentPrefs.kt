package tss.t.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** A show the user hid (iOS M6 N1 lite): kept on the device only, listed in Settings to unhide. */
data class HiddenShow(
    val feedId: Long,
    val title: String,
    val image: String? = null,
)

/**
 * What the user chose to see (parity with iOS D6 and M6 N1): explicit content is hidden by
 * default, and shows can be hidden one by one. Both live in [SharedPref], are read in memory
 * by the content filter on every list, and are exposed as flows for Settings.
 */
@Singleton
class ContentPrefs @Inject constructor(
    private val sharedPref: SharedPref,
) {
    private val _allowExplicit = MutableStateFlow(sharedPref.get<Boolean>(KEY_ALLOW_EXPLICIT) ?: false)
    val allowExplicit: StateFlow<Boolean> = _allowExplicit.asStateFlow()

    private val _hiddenShows = MutableStateFlow(loadHidden())
    val hiddenShows: StateFlow<List<HiddenShow>> = _hiddenShows.asStateFlow()

    fun setAllowExplicit(allow: Boolean) {
        sharedPref.save(KEY_ALLOW_EXPLICIT, allow)
        _allowExplicit.value = allow
    }

    fun isHidden(feedId: Long?): Boolean =
        feedId != null && _hiddenShows.value.any { it.feedId == feedId }

    /** Newest first; hiding a show again moves it to the top. */
    fun hide(show: HiddenShow) {
        _hiddenShows.update { list -> listOf(show) + list.filterNot { it.feedId == show.feedId } }
        persist()
    }

    fun unhide(feedId: Long) {
        _hiddenShows.update { list -> list.filterNot { it.feedId == feedId } }
        persist()
    }

    private fun persist() {
        sharedPref.save(KEY_HIDDEN_SHOWS, _hiddenShows.value.toTypedArray())
    }

    private fun loadHidden(): List<HiddenShow> =
        runCatching { sharedPref.get<Array<HiddenShow>>(KEY_HIDDEN_SHOWS)?.toList() }
            .getOrNull()
            .orEmpty()

    companion object {
        const val KEY_ALLOW_EXPLICIT = "AllowExplicitContent"
        const val KEY_HIDDEN_SHOWS = "HiddenShows"
    }
}

/** Look-and-feel choices kept on the device (Settings → Giao diện). */
@Singleton
class UiPrefs @Inject constructor(
    private val sharedPref: SharedPref,
) {
    private val _liquidGlass = MutableStateFlow(sharedPref.get<Boolean>(KEY_LIQUID_GLASS) ?: true)

    /** "Liquid Glass" surfaces (bars, mini player, Player buttons); on by default. */
    val liquidGlass: StateFlow<Boolean> = _liquidGlass.asStateFlow()

    fun setLiquidGlass(enabled: Boolean) {
        sharedPref.save(KEY_LIQUID_GLASS, enabled)
        _liquidGlass.value = enabled
    }

    companion object {
        const val KEY_LIQUID_GLASS = "LiquidGlass"
    }
}
