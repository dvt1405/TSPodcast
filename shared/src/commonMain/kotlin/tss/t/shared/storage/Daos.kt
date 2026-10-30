package tss.t.shared.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tss.t.shared.favourite.FavouriteType

/*
 * Ports of Android `coreApi/dao/{PodcastDao,EpisodeDao,FeedDao}` and `core/storage/dao/FavouriteDao`.
 *
 * Android stamps `cachedAt = System.currentTimeMillis()` inside `insert`/`inserts`; here the stamp
 * comes from the injected clock in [PodcastStorage], so these DAOs only write what they are given.
 * Inserts REPLACE like Android.
 */

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<PodcastEntity>)

    @Query("SELECT * FROM Podcast WHERE id = :id")
    suspend fun selectById(id: Long): PodcastEntity?

    /** Android `selectAllEpisodeById`: the podcast row [id] and every episode with `feedId == id`. */
    @Transaction
    @Query("SELECT * FROM Podcast WHERE id = :id")
    suspend fun selectAllEpisodeById(id: Long): PodcastWithEpisodes?

    /**
     * Drops expired rows the user has not favourited (Android `deleteExpired`), and **[Sửa]** also
     * keeps the podcast of a favourited episode: Android deletes it after 7 days, so the favourite
     * episode no longer opens offline (`selectAllEpisodeById` returns null). M4 §3.1.
     */
    @Query(
        "DELETE FROM Podcast WHERE cachedAt < :expiredBefore " +
            "AND CAST(id AS TEXT) NOT IN (SELECT id FROM FavouriteDTO) " +
            "AND CAST(feedId AS TEXT) NOT IN (SELECT id FROM FavouriteDTO) " +
            "AND id NOT IN (SELECT Episode.feedId FROM Episode INNER JOIN FavouriteDTO " +
            "ON FavouriteDTO.type = 'PodcastEpisode' AND FavouriteDTO.id = CAST(Episode.id AS TEXT))"
    )
    suspend fun deleteExpired(expiredBefore: Long): Int
}

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<EpisodeEntity>)

    @Query("SELECT * FROM Episode WHERE id = :id")
    suspend fun selectById(id: Long): EpisodeEntity?

    /** Saved episodes of a feed, newest first (E10 order), for Detail when E10 fails (M4 §3.3). */
    @Query("SELECT * FROM Episode WHERE feedId = :feedId ORDER BY datePublished DESC, id DESC")
    suspend fun selectByFeedId(feedId: Long): List<EpisodeEntity>

    /** Drops expired rows whose episode or feed the user has not favourited (Android `deleteExpired`). */
    @Query(
        "DELETE FROM Episode WHERE cachedAt < :expiredBefore " +
            "AND CAST(id AS TEXT) NOT IN (SELECT id FROM FavouriteDTO) " +
            "AND CAST(feedId AS TEXT) NOT IN (SELECT id FROM FavouriteDTO)"
    )
    suspend fun deleteExpired(expiredBefore: Long): Int
}

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<FeedEntity>)

    @Query("SELECT * FROM Feed WHERE id = :id")
    suspend fun selectById(id: Long): FeedEntity?

    /** Drops expired rows the user has not favourited (Android `deleteExpired`). */
    @Query(
        "DELETE FROM Feed WHERE cachedAt < :expiredBefore " +
            "AND CAST(id AS TEXT) NOT IN (SELECT id FROM FavouriteDTO)"
    )
    suspend fun deleteExpired(expiredBefore: Long): Int
}

@Dao
interface FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: FavouriteEntity)

    @Query("DELETE FROM FavouriteDTO WHERE type = :type AND id = :id")
    suspend fun delete(type: FavouriteType, id: String): Int

    @Query("SELECT * FROM FavouriteDTO WHERE id = :id AND type = :type")
    suspend fun selectById(id: String, type: FavouriteType): FavouriteEntity?

    /**
     * Every favourite, **newest first** (M4 §7 item 3; Android `getAll()` has no ORDER BY).
     * REPLACE deletes and re-inserts, so writing a favourite again moves it to the top.
     * Emits again after every write to the table.
     */
    @Query("SELECT * FROM FavouriteDTO ORDER BY rowid DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>

    @Query("SELECT * FROM FavouriteDTO ORDER BY rowid DESC")
    suspend fun getAll(): List<FavouriteEntity>

    @Query("SELECT COUNT(*) FROM FavouriteDTO")
    suspend fun count(): Int
}
