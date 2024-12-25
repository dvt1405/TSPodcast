package tss.t.core.repository

import tss.t.core.models.FavouriteDTO

enum class MediaType {
    Podcast,
    Radio,
    PodcastEpisode,
    TVChannel,
}

interface IFavouriteRepository {
    suspend fun saveFavourite(
        isFav: Boolean,
        favouriteDTO: FavouriteDTO
    )

    suspend fun isFavourite(
        id: String,
        type: MediaType
    ): Boolean

    suspend fun getFavouriteItem(
        id: String,
        type: MediaType
    ): FavouriteDTO?

    suspend fun getListFavourite(): List<FavouriteDTO>

    suspend fun getListFavourite(type: MediaType): List<FavouriteDTO>
}