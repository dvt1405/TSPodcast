package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterFeeds
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E8 `search/music/byterm` (`val=music`), blacklist-filtered on `feed.id` and `title`.
 * A blank [query] returns `Success(count = 0)` without a request.
 */
class SearchMusicFeeds internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
) {
    suspend operator fun invoke(
        query: String,
        max: Int = MAX_RESULTS,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
    ): TSDataState<SearchResponse> {
        val result = api.searchMusicPodcasts(
            query = query,
            max = max,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
        )
        return when (result) {
            is TSDataState.Success -> TSDataState.Success(result.data.filterFeeds(blacklist))
            else -> result
        }
    }

    companion object {
        const val MAX_RESULTS = 60
    }
}
