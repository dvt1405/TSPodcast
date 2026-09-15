package tss.t.coreapi.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tss.t.coreapi.models.Feed

@Dao
abstract class FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertInternal(podcast: Feed)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertsInternal(podcast: List<Feed>)

    /**
     * Stamps [cachedAt] so the row can be expired later.
     *
     * PodcastIndex's terms forbid keeping cached copies longer than the cache
     * header permits, and the API answers with `no-cache, must-revalidate`.
     */
    suspend fun insert(podcast: Feed) {
        insertInternal(podcast.copy(cachedAt = System.currentTimeMillis()))
    }

    /**
     * Stamps [cachedAt] so the row can be expired later.
     *
     * PodcastIndex's terms forbid keeping cached copies longer than the cache
     * header permits, and the API answers with `no-cache, must-revalidate`.
     */
    suspend fun inserts(podcast: List<Feed>) {
        val now = System.currentTimeMillis()
        insertsInternal(podcast.map { it.copy(cachedAt = now) })
    }

    @Delete
    abstract suspend fun delete(podcast: Feed)

    @Query("Select * from Feed where id=:id")
    abstract suspend fun selectById(id: Long): Feed?

    /** Drops expired rows that the user has not favourited. */
    @Query(
        "DELETE FROM Feed WHERE cachedAt < :expiredBefore " +
                "AND CAST(id AS TEXT) NOT IN (SELECT id FROM FavouriteDTO)"
    )
    abstract suspend fun deleteExpired(expiredBefore: Long): Int
}
