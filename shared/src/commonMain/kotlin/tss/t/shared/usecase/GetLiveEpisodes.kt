package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterLive
import tss.t.shared.model.LiveResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E2 `episodes/live`, blacklist-filtered. No cache and no retry: Android's `retryWhen` wraps
 * a `flowOf` of a value that is already fetched, so it never sends the request again.
 */
class GetLiveEpisodes internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
) {
    suspend operator fun invoke(max: Int = 100): TSDataState<LiveResponse> =
        when (val result = api.getLiveEpisodes(max = max)) {
            is TSDataState.Success -> TSDataState.Success(result.data.filterLive(blacklist))
            else -> result
        }
}
