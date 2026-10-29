package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterFeeds
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E7 `search/byterm`, blacklist-filtered on `feed.id` and `title`.
 *
 * Android's `pretty` parameter is not carried over: `pretty` is a presence flag, so sending
 * `pretty=false` only asked for pretty-printed JSON.
 */
class SearchPodcasts internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
) {
    suspend operator fun invoke(
        query: String,
        type: String? = null,
        max: Int = 100,
        aponly: Boolean = false,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
    ): TSDataState<SearchResponse> {
        val result = api.searchPodcasts(
            query = query,
            type = type,
            max = max,
            aponly = aponly,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
        )
        return when (result) {
            is TSDataState.Success -> TSDataState.Success(result.data.filterFeeds(blacklist))
            else -> result
        }
    }
}
