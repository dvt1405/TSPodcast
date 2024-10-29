package tss.t.podcasts.usecase

import androidx.annotation.IntRange
import kotlinx.coroutines.flow.map
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import javax.inject.Inject

class GetRecentFeeds @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        @IntRange(from = 1, to = 1000)
        max: Int = 100,
        since: Long? = null,
        lang: String = "vi",
        cat: String?,
        notcat: String? = null,
        pretty: Boolean? = null
    ) = repository.getRecentFeeds(
        max = max,
        since = since,
        lang = lang,
        cat = cat,
        notcat = notcat,
        pretty = pretty
    ).map { rs ->
        if (rs is TSDataState.Success) {
            val data = rs.data
            data.type = TrendingPodcastRes.Type.RecentFeed
            TSDataState.Success(data)
        } else {
            rs
        }
    }
}