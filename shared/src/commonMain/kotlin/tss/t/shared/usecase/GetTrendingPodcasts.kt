package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterTrending
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.PodcastIndexApi

/**
 * E1 `podcasts/trending`, blacklist-filtered and cached in memory for 15 minutes.
 *
 * The cache key is [cat] normalized (null and blank share one entry), never [since]: callers
 * compute `since` from the current time, so it differs on every call. Android keeps one slot
 * for "no category" and a single slot for every other category; here each category has its
 * own entry ([Sửa]). The disk copy Android keeps (`cachedTrending*`) comes with the
 * persistence milestone.
 *
 * @param since epoch **seconds**, required (features-spec §2 [Sửa]: Android's default
 *   overflowed).
 */
class GetTrendingPodcasts internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
    private val cache: InMemoryTtlCache<String, TrendingPodcastRes>,
) {
    suspend operator fun invoke(
        max: Int,
        since: Long,
        lang: String = "vi",
        cat: String? = null,
        notcat: String? = null,
    ): TSDataState<TrendingPodcastRes> {
        val key = cat?.trim().orEmpty()
        cache.get(key)?.let { return TSDataState.Success(it) }

        val result = api.getTrending(
            since = since,
            max = max,
            lang = lang,
            cat = cat,
            notcat = notcat,
        )
        if (result !is TSDataState.Success) return result
        val filtered = result.data.filterTrending(blacklist)
        cache.put(key, filtered)
        return TSDataState.Success(filtered)
    }
}
