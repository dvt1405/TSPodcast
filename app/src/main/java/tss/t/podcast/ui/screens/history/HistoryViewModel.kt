package tss.t.podcast.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tss.t.core.history.HistoryDay
import tss.t.core.history.ListeningHistoryRules
import tss.t.core.models.ListeningHistory
import tss.t.podcasts.usecase.history.ListeningHistoryUseCase
import tss.t.sharedplayer.player.PlayerManager
import javax.inject.Inject

/** Local listening history ("Lịch sử") and the Home "Nghe tiếp" row. Nothing leaves the device. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyUseCase: ListeningHistoryUseCase,
    private val playerManager: PlayerManager,
) : ViewModel() {

    /** History grouped by day, newest first; null while loading. */
    val days: StateFlow<List<HistoryDay>?> = historyUseCase.history()
        .map { ListeningHistoryRules.groupByDay(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    /** Episodes left part-way, for the "Nghe tiếp" row on Home. */
    val continueListening: StateFlow<List<ListeningHistory>> = historyUseCase.continueListening()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** "Xoá khỏi lịch sử": the row and the saved position of that episode. */
    fun remove(entry: ListeningHistory) {
        viewModelScope.launch {
            historyUseCase.remove(entry.mediaId)
            playerManager.forgetPosition(entry.mediaId)
        }
    }

    /** "Xoá lịch sử": every row and every saved position (playback speeds are kept). */
    fun clearAll() {
        viewModelScope.launch {
            historyUseCase.clear()
            playerManager.forgetAllPositions()
        }
    }
}
