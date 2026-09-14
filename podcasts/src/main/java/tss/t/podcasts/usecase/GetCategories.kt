package tss.t.podcasts.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.TSDataState
import javax.inject.Inject

class GetCategories @Inject constructor(
    private val repository: IPodcastRepository
) {
    suspend operator fun invoke(useCache: Boolean = false): Flow<TSDataState<CategoryRes>> {
        // Read once into a local: _cache is a mutable companion field reachable
        // from several coroutines, so the null check and the !! were separate
        // reads of a value that could change in between.
        val cached = _cache
        if (useCache && cached != null) {
            return flowOf(TSDataState.Success(cached))
        }
        return repository.getCategory().onEach {
            if (it is TSDataState.Success) {
                synchronized(cacheLock) {
                    _cache = it.data
                }
            }
        }
    }

    companion object {
        private val cacheLock by lazy { Any() }

        @Volatile
        private var _cache: CategoryRes? = null
    }
}