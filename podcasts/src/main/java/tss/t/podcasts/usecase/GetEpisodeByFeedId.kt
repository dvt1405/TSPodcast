package tss.t.podcasts.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.TSDataState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class GetEpisodeByFeedId @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(
        id: String
    ): Flow<TSDataState<EpisodeResponse>> {
        // Single read of each map; `_cache[id]!!` after a separate containment
        // check could NPE if another coroutine evicted the entry in between.
        val cached = _cache[id]
        val cachedAt = _lastCache[id]
        if (cached != null && cachedAt != null &&
            System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
        ) {
            return flowOf(TSDataState.Success(cached))
        }
        return repository.getEpisodeByFeedId(
            id
        ).onEach {
            if (it is TSDataState.Success) {
                val data = it.data
                _lastCache[id] = System.currentTimeMillis()
                _cache[id] = data
            }
        }
    }

    companion object {
        private val _cache by lazy {
            ConcurrentHashMap<String, EpisodeResponse>()
        }
        private val _lastCache by lazy {
            ConcurrentHashMap<String, Long>()
        }
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}