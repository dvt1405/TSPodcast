package tss.t.shared.usecase

import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.model.PodcastByFeedIdRes
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E11 `podcasts/byfeedid?id=&pretty=true`. Not blacklist-filtered.
 *
 * Retries up to 3 times on HTTP/IO errors, but not when the body does not parse or on an
 * `IllegalStateException` (Android's `retryWhen { cause !is IllegalStateException && attempt < 3 }`).
 * Successful results are cached for 15 minutes per [feedId].
 */
class GetPodcastByFeedID internal constructor(
    private val api: PodcastIndexApi,
    private val cache: InMemoryTtlCache<String, PodcastByFeedIdRes>,
) {
    suspend operator fun invoke(feedId: String): TSDataState<PodcastByFeedIdRes> {
        cache.get(feedId)?.let { return TSDataState.Success(it) }
        val result = withRetry(RetryPolicy.NetworkErrors) {
            api.getPodcastByFeedId(id = feedId, pretty = true)
        }
        if (result is TSDataState.Success) cache.put(feedId, result.data)
        return result
    }
}
