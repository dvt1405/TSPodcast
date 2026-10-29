package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterRecentFeeds
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.PodcastIndexApi

/**
 * E5 `recent/feeds`, blacklist-filtered. No cache and no retry (same reason as
 * [GetLiveEpisodes]).
 *
 * @param since epoch seconds, or null for the API default.
 */
class GetRecentFeeds internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
) {
    suspend operator fun invoke(
        max: Int = 100,
        since: Long? = null,
        lang: String = "vi",
        cat: String?,
        notcat: String? = null,
    ): TSDataState<TrendingPodcastRes> {
        val result = api.getRecentFeeds(
            max = max,
            since = since,
            lang = lang,
            cat = cat,
            notcat = notcat,
        )
        return when (result) {
            is TSDataState.Success -> TSDataState.Success(result.data.filterRecentFeeds(blacklist))
            else -> result
        }
    }
}
