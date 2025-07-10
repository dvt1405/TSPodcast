package tss.t.core.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.MediaType

@Dao
abstract class FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(favouriteDTO: FavouriteDTO)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserts(favourites: List<FavouriteDTO>)

    @Delete
    abstract suspend fun delete(favouriteDTO: FavouriteDTO)

    @Query("Delete from FavouriteDTO where id=:id")
    abstract suspend fun delete(id: String)

    @Query("Select * from FavouriteDTO where id=:id and type=:mediaType")
    abstract suspend fun selectById(
        id: String,
        mediaType: MediaType
    ): FavouriteDTO?

    @Query("Select * from FavouriteDTO")
    abstract suspend fun getAll(): List<FavouriteDTO>

    @Query("Select * from FavouriteDTO where type=:type")
    abstract suspend  fun getAllByType(type: MediaType): List<FavouriteDTO>
}