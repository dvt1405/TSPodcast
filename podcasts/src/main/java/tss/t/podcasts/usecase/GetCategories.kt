package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import javax.inject.Inject

class GetCategories @Inject constructor(
    private val repository: IPodcastRepository
) {
    operator suspend fun invoke() {
        repository.getCategory()
    }
}