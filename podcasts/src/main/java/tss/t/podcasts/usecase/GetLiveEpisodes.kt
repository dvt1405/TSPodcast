package tss.t.podcasts.usecase

import androidx.annotation.IntRange
import kotlinx.coroutines.flow.Flow
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.TSDataState
import javax.inject.Inject

class GetLiveEpisodes @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        @IntRange(from = 1, to = 1000)
        max: Int = 100
    ): Flow<TSDataState<LiveResponse>> {
        return repository.getLiveEpisodes(
            max
        )
    }
}