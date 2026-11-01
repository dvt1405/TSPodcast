package tss.t.podcasts.usecase.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import tss.t.core.history.ListeningHistoryRules
import tss.t.core.models.ListeningHistory
import tss.t.core.repository.IHistoryRepository
import javax.inject.Inject

/** Reads and edits the local listening history for the UI (History screen, "Nghe tiếp"). */
class ListeningHistoryUseCase @Inject constructor(
    private val historyRepository: IHistoryRepository
) {
    fun history(): Flow<List<ListeningHistory>> =
        historyRepository.observeHistory().flowOn(Dispatchers.IO)

    fun continueListening(
        limit: Int = ListeningHistoryRules.CONTINUE_LIMIT
    ): Flow<List<ListeningHistory>> =
        historyRepository.observeContinueListening(limit).flowOn(Dispatchers.IO)

    suspend fun latestResumable(): ListeningHistory? =
        withContext(Dispatchers.IO) { historyRepository.latestResumable() }

    suspend fun remove(mediaId: String) =
        withContext(Dispatchers.IO) { historyRepository.removeFromHistory(mediaId) }

    suspend fun clear() = withContext(Dispatchers.IO) { historyRepository.clearHistory() }
}
