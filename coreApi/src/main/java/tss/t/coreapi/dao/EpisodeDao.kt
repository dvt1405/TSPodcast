package tss.t.coreapi.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tss.t.coreapi.models.Episode

@Dao
abstract class EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(podcast: Episode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserts(podcast: List<Episode>)

    @Delete
    abstract suspend fun delete(podcast: Episode)

    @Query("Select * from Episode where id=:id")
    abstract suspend fun selectById(id: Long): Episode?
}