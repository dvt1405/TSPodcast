package tss.t.coreapi.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.databaseview.PodcastAndEpisode

@Dao
abstract class PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertInternal(podcast: Podcast)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertsInternal(podcast: List<Podcast>)

    /**
     * Stamps [cachedAt] so the row can be expired later.
     *
     * PodcastIndex's terms forbid keeping cached copies longer than the cache
     * header permits, and the API answers with `no-cache, must-revalidate`.
     */
    suspend fun insert(podcast: Podcast) {
        insertInternal(podcast.copy(cachedAt = System.currentTimeMillis()))
    }

    /**
     * Stamps [cachedAt] so the row can be expired later.
     *
     * PodcastIndex's terms forbid keeping cached copies longer than the cache
     * header permits, and the API answers with `no-cache, must-revalidate`.
     */
    suspend fun inserts(podcast: List<Podcast>) {
        val now = System.currentTimeMillis()
        insertsInternal(podcast.map { it.copy(cachedAt = now) })
    }

    @Delete
    abstract suspend fun delete(podcast: Podcast)

    @Query("Select * from Podcast where id=:id")
    abstract suspend fun selectById(id: Long): Podcast?

    @Transaction
    @Query("Select * from Podcast where id=:id")
    abstract suspend fun selectAllEpisodeById(id: Long): PodcastAndEpisode?

    /** Drops expired rows that the user has not favourited. */
    @Query(
        "DELETE FROM Podcast WHERE cachedAt < :expiredBefore " +
                "AND CAST(id AS TEXT) NOT IN (SELECT id FROM FavouriteDTO) " +
                "AND CAST(feedId AS TEXT) NOT IN (SELECT id FROM FavouriteDTO)"
    )
    abstract suspend fun deleteExpired(expiredBefore: Long): Int
}
