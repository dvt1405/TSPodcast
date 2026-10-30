package tss.t.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Android `MediaType` values a favourite can have on iOS v1 (no radio, no TV). */
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
 * Favourites toggled from the Player (`SaveFavouriteUseCase` / `DeleteFavouriteUseCase`).
 * In memory until Room KMP (M4): the Favourite tab will observe [items].
 */
class FavouriteStore {
    private val state = MutableStateFlow<List<FavouriteItem>>(emptyList())

    /** Newest first. */
    val items: StateFlow<List<FavouriteItem>> = state.asStateFlow()

    fun isFavourite(type: FavouriteType, id: String): Boolean =
        state.value.any { it.type == type && it.id == id }

    fun setFavourite(item: FavouriteItem, isFavourite: Boolean) {
        state.update { current ->
            val others = current.filterNot { it.type == item.type && it.id == item.id }
            if (isFavourite) listOf(item) + others else others
        }
    }

    /** Flips [item] and returns the new value. */
    fun toggle(item: FavouriteItem): Boolean {
        val newValue = !isFavourite(item.type, item.id)
        setFavourite(item, newValue)
        return newValue
    }
}
