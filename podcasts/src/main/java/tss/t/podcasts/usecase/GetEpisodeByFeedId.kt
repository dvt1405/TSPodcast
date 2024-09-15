package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import javax.inject.Inject

class GetEpisodeByFeedId @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        id: String
    ) = repository.getEpisodeByFeedId(
        id
    )
}