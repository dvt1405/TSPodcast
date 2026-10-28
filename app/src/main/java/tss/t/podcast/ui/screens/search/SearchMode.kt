package tss.t.podcast.ui.screens.search

import androidx.annotation.StringRes
import tss.t.podcast.R

/**
 * Which PodcastIndex search endpoint the query is sent to.
 *
 * [Music] uses `search/music/byterm`, i.e. feeds published with
 * `podcast:medium = music`. Their items are tracks rather than episodes, but
 * they are ordinary RSS enclosures, so the detail screen and the player handle
 * them unchanged.
 */
enum class SearchMode(@StringRes val labelRes: Int) {
    All(R.string.search_mode_all),
    Music(R.string.search_mode_music)
}
