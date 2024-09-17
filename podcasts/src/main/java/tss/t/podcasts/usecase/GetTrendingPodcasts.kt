package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import javax.inject.Inject

class GetTrendingPodcasts @Inject constructor(
    private val repository: IPodcastRepository
) {
    var lastUpdate = 0L
    var cachedData: TrendingPodcastRes? = null
    val lock = Any()
    suspend operator fun invoke(
        max: Int,
        since: Int,
        lang: String = "vi",
        cat: String? = null,
        notcat: String? = null,
        pretty: Boolean? = null
    ): TSDataState<TrendingPodcastRes> {

        if (cachedData != null && System.currentTimeMillis() - lastUpdate < 15 * A_MINUTES) {
            return TSDataState.Success(cachedData!!)
        }

        return repository.getTrending(
            max = max,
            since = since,
            lang = lang,
            cat = cat,
            notcat = notcat,
            pretty = pretty
        ).also {
            synchronized(lock) {
                if (it is TSDataState.Success) {
                    cachedData = it.data
                    lastUpdate = System.currentTimeMillis()
                }
            }
        }
    }

    companion object {
        private const val A_MINUTES = 60 * 1000
    }

}