package tss.t.podcasts.usecase.favourite

import tss.t.core.repository.IFavouriteRepository
import javax.inject.Inject

class SelectAllFavourite @Inject constructor(
    private val repository: IFavouriteRepository
) {
    suspend operator fun invoke() = repository.getListFavourite()
}