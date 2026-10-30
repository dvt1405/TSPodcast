package tss.t.shared.usecase

import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.model.EpisodeResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexApi

/**
 * E10 `episodes/byfeedid?id=&max=100`. Not blacklist-filtered (the feed was already shown).
 *
 * Retries up to 3 times on any error (4 requests at most), immediately, like Android's
 * `retryWhen { attempt < 3 }`. Successful results are cached for 15 minutes per id and size.
 */
class GetEpisodeByFeedId internal constructor(
    private val api: PodcastIndexApi,
    private val cache: InMemoryTtlCache<String, EpisodeResponse>,
) {
    suspend operator fun invoke(id: String): TSDataState<EpisodeResponse> = invoke(id, DEFAULT_MAX)

    /**
     * Same call with another `max` (M6: 1 000 for "Tìm trong show", 20 for the news hub). The
     * Detail size keeps its original cache key ([id]); any other size has its own entry
     * (`"<id>?max=<max>"`) in the same 15-minute cache, so the two never answer for each other.
     */
    suspend operator fun invoke(id: String, max: Int): TSDataState<EpisodeResponse> {
        val key = cacheKey(id, max)
        cache.get(key)?.let { return TSDataState.Success(it) }
        val result = withRetry(RetryPolicy.AnyError) { api.getEpisodesByFeedId(id = id, max = max) }
        if (result is TSDataState.Success) cache.put(key, result.data)
        return result
    }

    companion object {
        /** E10 of Podcast detail (Android `max=100`). */
        const val DEFAULT_MAX = 100

        fun cacheKey(id: String, max: Int): String = if (max == DEFAULT_MAX) id else "$id?max=$max"
    }
}
