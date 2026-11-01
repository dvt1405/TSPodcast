package tss.t.core.repository

import kotlinx.coroutines.flow.Flow
import tss.t.core.models.ListeningHistory
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast

interface IHistoryRepository {
    suspend fun saveCurrentPlaying(mediaItem: String)

    suspend fun saveCurrentPlaying(
        episode: Episode,
        podcast: Podcast?,
        listItem: List<Episode>
    )

    suspend fun saveCurrentPlaying(
        mediaItemId: String,
        podcast: Podcast,
        listItem: List<Episode>
    )

    // ---------------------------------------------------------------------------------------
    // Local listening history ("Lịch sử") and "Nghe tiếp"
    // ---------------------------------------------------------------------------------------

    /** Whole history, last played first. */
    fun observeHistory(): Flow<List<ListeningHistory>>

    /** Episodes left part-way, last played first, at most [limit]. */
    fun observeContinueListening(limit: Int): Flow<List<ListeningHistory>>

    /** The episode to offer on launch: the last one left part-way, null when none. */
    suspend fun latestResumable(): ListeningHistory?

    /** The last played episode, heard or not, null when the history is empty. */
    suspend fun latestPlayed(): ListeningHistory?

    suspend fun historyEntry(mediaId: String): ListeningHistory?

    /** Merges a player snapshot into the history (see `ListeningHistoryRules.merge`) and prunes. */
    suspend fun recordProgress(snapshot: ListeningHistory)

    suspend fun removeFromHistory(mediaId: String)

    suspend fun clearHistory()
}
