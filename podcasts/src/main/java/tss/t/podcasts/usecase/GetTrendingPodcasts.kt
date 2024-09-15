package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import javax.inject.Inject

class GetTrendingPodcasts @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        max: Int,
        since: Int,
        lang: String = "vi,en",
        cat: String? = null,
        notcat: String? = null,
        pretty: Boolean? = null
    ): TSDataState<TrendingPodcastRes> = repository.getTrending(
        max = max,
        since = since,
        lang = lang,
        cat = cat,
        notcat = notcat,
        pretty = pretty
    )

}