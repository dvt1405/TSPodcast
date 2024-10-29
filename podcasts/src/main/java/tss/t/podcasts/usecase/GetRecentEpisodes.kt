package tss.t.podcasts.usecase

import androidx.annotation.IntRange
import kotlinx.coroutines.flow.map
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.TSDataState
import javax.inject.Inject

class GetRecentEpisodes @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        excludeString: String? = null,
        since: Long? = null,
        fulltext: String? = null,
        pretty: Boolean? = null
    ) = repository.getRecentEpisodes(
        max = max,
        excludeString = excludeString,
        since = since,
        fulltext = fulltext,
        pretty = pretty
    ).map { rs ->
        if (rs is TSDataState.Success) {
            val data = rs.data
            data.type = EpisodeResponse.Type.RecentEpisode
            TSDataState.Success(data)
        } else {
            rs
        }
    }
}