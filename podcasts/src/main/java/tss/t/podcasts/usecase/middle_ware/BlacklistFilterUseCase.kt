package tss.t.podcasts.usecase.middle_ware

import tss.t.podcasts.BlacklistRepositoryImpl
import javax.inject.Inject

class BlacklistFilterUseCase @Inject constructor(
    private val blacklistRepository: BlacklistRepositoryImpl
) {
    suspend operator fun invoke(id: String) = blacklistRepository.isInBlacklist(id)
}