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
    abstract fun insert(podcast: Podcast)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun inserts(podcast: List<Podcast>)

    @Delete
    abstract fun delete(podcast: Podcast)

    @Query("Select * from Podcast where id=:id")
    abstract fun selectById(id: Long): Podcast?

    @Transaction
    @Query("Select * from Podcast where id=:id")
    abstract fun selectAllEpisodeById(id: Long): PodcastAndEpisode?
}