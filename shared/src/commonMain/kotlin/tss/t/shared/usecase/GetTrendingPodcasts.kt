package tss.t.shared.usecase

import tss.t.shared.blacklist.BlacklistRepository
import tss.t.shared.blacklist.filterTrending
import tss.t.shared.cache.InMemoryTtlCache
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.PodcastIndexApi
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.util.EpochClock

/**
 * E1 `podcasts/trending`, blacklist-filtered and cached for 15 minutes in memory and on disk,
 * like Android's `GetTrendingPodcasts`:
 * 1. the memory entry for [cat], when fresh;
 * 2. otherwise the disk copy (`cachedTrending` / `lastUpdateTrending` without a category,
 *    `cachedTrending_<cat>` / `lastUpdateTrending_<cat>` with one), when younger than 15
 *    minutes; it is put back in memory with its original timestamp;
 * 3. otherwise the network. Only a `Success` is stored, already filtered, in both tiers.
 *
 * The cache key is [cat] normalized (null and blank share one entry), never [since]: callers
 * compute `since` from the current time, so it differs on every call. Android keeps one slot
 * for "no category" and a single slot for every other category; here each category has its
 * own entry ([Sửa]). A disk copy that does not parse is ignored.
 *
 * @param since epoch **seconds**, required (features-spec §2 [Sửa]: Android's default
 *   overflowed).
 */
class GetTrendingPodcasts internal constructor(
    private val api: PodcastIndexApi,
    private val blacklist: BlacklistRepository,
    private val cache: InMemoryTtlCache<String, TrendingPodcastRes>,
    private val preferences: AppPreferences,
    private val clock: EpochClock,
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
        fromDisk(key)?.let { return TSDataState.Success(it) }

        val result = api.getTrending(
            since = since,
            max = max,
            lang = lang,
            cat = cat,
            notcat = notcat,
        )
        if (result !is TSDataState.Success) return result
        val filtered = result.data.filterTrending(blacklist)
        val now = clock.nowMillis()
        cache.put(key, filtered, storedAtMillis = now)
        preferences.saveTrending(key, filtered, savedAtMillis = now)
        return TSDataState.Success(filtered)
    }

    private suspend fun fromDisk(key: String): TrendingPodcastRes? {
        val cached = preferences.cachedTrending(key) ?: return null
        if (clock.nowMillis() - cached.savedAtMillis >= InMemoryTtlCache.API_CACHE_TTL.inWholeMilliseconds) {
            return null
        }
        cache.put(key, cached.value, storedAtMillis = cached.savedAtMillis)
        return cached.value
    }
}
