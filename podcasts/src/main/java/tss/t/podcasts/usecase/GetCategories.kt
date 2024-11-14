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
        if (useCache && _cache != null) {
            return flowOf(TSDataState.Success(_cache!!))
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
        private var _cache: CategoryRes? = null
    }
}