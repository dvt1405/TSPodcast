package tss.t.podcast.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.analytics.Analytics
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import tss.t.sharedlibrary.analytics.AppEvents

/**
 * The Search tab searches as the user types (200 ms debounce), so one query produces many
 * results. This logs GA4 `search` (and `search_no_results`) once the query has stayed the
 * same for [settleMs], and not again for the same query and mode.
 */
class SettledSearchLogger(
    private val scope: CoroutineScope,
    private val settleMs: Long = 1_500L,
    private val tracker: AnalyticsTracker = Analytics,
) {
    private var job: Job? = null
    private var lastLogged: String? = null

    /** Results of [term] arrived ([resultCount] after filtering); [mode] = `all` | `music`. */
    @Synchronized
    fun onResult(term: String, mode: String, resultCount: Int) {
        job?.cancel()
        if (term.isBlank()) return
        job = scope.launch {
            delay(settleMs)
            val key = "$mode|${term.trim().lowercase()}"
            if (key == lastLogged) return@launch
            lastLogged = key
            tracker.log(AppEvents.search(term, mode, resultCount))
            if (resultCount == 0) tracker.log(AppEvents.searchNoResults(term, mode))
        }
    }
}
