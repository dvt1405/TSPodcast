package tss.t.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tss.t.core.models.ListeningHistory

@Dao
abstract class ListeningHistoryDao {

    @Upsert
    abstract suspend fun upsert(entry: ListeningHistory)

    @Query("SELECT * FROM ListeningHistory WHERE mediaId = :mediaId")
    abstract suspend fun selectById(mediaId: String): ListeningHistory?

    /** Whole history, last played first. */
    @Query("SELECT * FROM ListeningHistory ORDER BY lastPlayedAt DESC")
    abstract fun observeAll(): Flow<List<ListeningHistory>>

    /** Episodes left part-way ("Nghe tiếp"), last played first. */
    @Query(
        "SELECT * FROM ListeningHistory WHERE finished = 0 AND positionMs >= :minPositionMs " +
            "ORDER BY lastPlayedAt DESC LIMIT :limit"
    )
    abstract fun observeUnfinished(minPositionMs: Long, limit: Int): Flow<List<ListeningHistory>>

    @Query(
        "SELECT * FROM ListeningHistory WHERE finished = 0 AND positionMs >= :minPositionMs " +
            "ORDER BY lastPlayedAt DESC LIMIT 1"
    )
    abstract suspend fun latestUnfinished(minPositionMs: Long): ListeningHistory?

    @Query("SELECT * FROM ListeningHistory ORDER BY lastPlayedAt DESC LIMIT 1")
    abstract suspend fun latest(): ListeningHistory?

    @Query("DELETE FROM ListeningHistory WHERE mediaId = :mediaId")
    abstract suspend fun delete(mediaId: String)

    @Query("DELETE FROM ListeningHistory")
    abstract suspend fun clear()

    /** Keeps the [keep] most recently played rows. */
    @Query(
        "DELETE FROM ListeningHistory WHERE mediaId NOT IN " +
            "(SELECT mediaId FROM ListeningHistory ORDER BY lastPlayedAt DESC LIMIT :keep)"
    )
    abstract suspend fun prune(keep: Int): Int
}
