package tss.t.shared.usecase

import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.model.EpisodeResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E10 `episodes/byfeedid?id=&max=100`. Not blacklist-filtered (the feed was already shown).
 *
 * Retries up to 3 times on any error (4 requests at most), immediately, like Android's
 * `retryWhen { attempt < 3 }`. Successful results are cached for 15 minutes per [id].
 */
class GetEpisodeByFeedId internal constructor(
    private val api: PodcastIndexApi,
    private val cache: InMemoryTtlCache<String, EpisodeResponse>,
) {
    suspend operator fun invoke(id: String): TSDataState<EpisodeResponse> {
        cache.get(id)?.let { return TSDataState.Success(it) }
        val result = withRetry(RetryPolicy.AnyError) { api.getEpisodesByFeedId(id = id) }
        if (result is TSDataState.Success) cache.put(id, result.data)
        return result
    }
}
