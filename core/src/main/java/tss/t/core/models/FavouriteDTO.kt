package tss.t.core.models

import androidx.room.Entity
import tss.t.core.repository.MediaType

@Entity(
    primaryKeys = ["type", "id"]
)
data class FavouriteDTO(
    val id: String,
    val type: MediaType,
    val title: String?,
    val image: String?,
    val description: String?
) {
}