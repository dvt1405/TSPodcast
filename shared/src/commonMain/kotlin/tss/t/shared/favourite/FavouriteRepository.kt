package tss.t.shared.favourite

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.shared.storage.FavouriteDao
import tss.t.shared.storage.FavouriteEntity

/**
 * Android `MediaType` values a favourite can have on iOS v1 (no radio, no TV; D1). Stored in
 * `FavouriteDTO.type` as the enum name, the same strings Android writes.
 */
enum class FavouriteType { Podcast, PodcastEpisode }

/** Android `FavouriteDTO`: primary key `(type, id)`. */
data class FavouriteItem(
    val id: String,
    val type: FavouriteType,
    val title: String,
    val image: String?,
    val description: String?,
)

/**
 * Favourites in the `FavouriteDTO` table: Android `SaveFavouriteUseCase`,
 * `DeleteFavouriteUseCase`, `IsFavouriteUseCase` and `SelectAllFavourite`, plus **[Sửa]** an
 * observable [items] so the Favourite tab follows changes made in the Player (features-spec §3.8).
 */
class FavouriteRepository(private val dao: FavouriteDao) {

    /** Every favourite, newest first; emits again after every change. */
    val items: Flow<List<FavouriteItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    suspend fun isFavourite(type: FavouriteType, id: String): Boolean = dao.selectById(id, type) != null

    suspend fun setFavourite(item: FavouriteItem, isFavourite: Boolean) {
        if (isFavourite) dao.insert(item.toEntity()) else dao.delete(item.type, item.id)
    }

    /** Flips [item] and returns the new value. */
    suspend fun toggle(item: FavouriteItem): Boolean {
        val newValue = !isFavourite(item.type, item.id)
        setFavourite(item, newValue)
        return newValue
    }

    suspend fun count(): Int = dao.count()
}

private fun FavouriteEntity.toItem() = FavouriteItem(
    id = id,
    type = type,
    title = title.orEmpty(),
    image = image,
    description = description,
)

private fun FavouriteItem.toEntity() = FavouriteEntity(
    id = id,
    type = type,
    title = title,
    image = image,
    description = description,
)
