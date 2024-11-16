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
    abstract fun insert(podcast: Feed)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun inserts(podcast: List<Feed>)

    @Delete
    abstract fun delete(podcast: Feed)

    @Query("Select * from Feed where id=:id")
    abstract fun selectById(id: Long): Feed?
}
