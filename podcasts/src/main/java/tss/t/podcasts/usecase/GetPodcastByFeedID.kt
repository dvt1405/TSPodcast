package tss.t.podcasts.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.BlacklistRepositoryImpl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class GetPodcastByFeedID @Inject constructor(
    private val repository: IPodcastRepository,
    private val blacklistRepository: BlacklistRepositoryImpl
) {

    suspend operator fun invoke(feedId: String): Flow<TSDataState<PodcastByFeedIdRes>> {
        // Single read of each map; `_cache[feedId]!!` after a separate
        // containment check could NPE if another coroutine evicted the entry.
        val cached = _cache[feedId]
        val cachedAt = _lastCache[feedId]
        if (cached != null && cachedAt != null &&
            System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
        ) {
            return flowOf(TSDataState.Success(cached))
        }
        return repository.getPodcastByFeedId(
            feedId,
            true
        ).onEach {
            if (it is TSDataState.Success) {
                val data = it.data
                _lastCache[feedId] = System.currentTimeMillis()
                _cache[feedId] = data
            }
        }
    }

    companion object {
        private val _cache by lazy {
            ConcurrentHashMap<String, PodcastByFeedIdRes>()
        }
        private val _lastCache by lazy {
            ConcurrentHashMap<String, Long>()
        }
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}