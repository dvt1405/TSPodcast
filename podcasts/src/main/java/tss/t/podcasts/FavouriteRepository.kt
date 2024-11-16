package tss.t.podcasts

import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.MediaType
import tss.t.core.storage.dao.FavouriteDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouriteRepository @Inject constructor(
    private val favouriteDao: FavouriteDao
) : IFavouriteRepository {
    override suspend fun saveFavourite(isFav: Boolean, favouriteDTO: FavouriteDTO) {
        if (isFav) {
            favouriteDao.insert(favouriteDTO)
        } else {
            favouriteDao.delete(favouriteDTO.id)
        }
    }

    override suspend fun isFavourite(
        id: String,
        type: MediaType
    ): Boolean {
        return kotlin.runCatching {
            favouriteDao.selectById(id, type)
        }.getOrNull() != null
    }

    override suspend fun getFavouriteItem(id: String, type: MediaType): FavouriteDTO? {
        return favouriteDao.selectById(id, type)
    }

    override suspend fun getListFavourite(): List<FavouriteDTO> {
        return favouriteDao.getAll()
    }

    override suspend fun getListFavourite(type: MediaType): List<FavouriteDTO> {
        return favouriteDao.getAllByType(type)
    }
}