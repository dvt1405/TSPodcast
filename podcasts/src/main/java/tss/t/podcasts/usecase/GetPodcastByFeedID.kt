package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import javax.inject.Inject

class GetPodcastByFeedID @Inject constructor(
    private val repository: IPodcastRepository
) {

    suspend operator fun invoke(feedId: String) = repository.getPodcastByFeedId(
        feedId,
        true
    )
}