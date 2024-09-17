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
        if (_lastCache[id] != null && _cache[id] != null) {
            val time = _lastCache[id] ?: 0L
            if (System.currentTimeMillis() - time < 15 * 60 * 1000) {
                return flowOf(TSDataState.Success(_cache[id]!!))
            }
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
    }
}