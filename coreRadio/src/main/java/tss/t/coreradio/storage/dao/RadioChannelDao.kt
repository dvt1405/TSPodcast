package tss.t.coreradio.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tss.t.coreradio.models.RadioChannel

@Dao
abstract class RadioChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(radioChannel: RadioChannel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserts(radioChannels: List<RadioChannel>)

    @Delete
    abstract suspend fun delete(favouriteDTO: RadioChannel)

    @Query("Delete from RadioChannel where channelId=:id")
    abstract suspend fun delete(id: String)

    @Query("Select * from RadioChannel")
    abstract fun getAll(): List<RadioChannel>

}