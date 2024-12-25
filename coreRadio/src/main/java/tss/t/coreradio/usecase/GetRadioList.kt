package tss.t.coreradio.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.di.RadioRepo
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import javax.inject.Inject

class GetRadioList @Inject constructor(
    private val apis: Map<RadioRepo, @JvmSuppressWildcards RadioApi>,
    private val radioChannelDao: RadioChannelDao
) {
    suspend operator fun invoke(
        repoKey: RadioRepo
    ): Flow<Result<List<RadioChannel>>> {
        val repo = apis[repoKey]
        assert(repo != null) {
            "Not found repository for key: $repoKey"
        }
        return flowOf(repo!!.getRadioList())
            .retry(2)
            .map {
                if (it.isNotEmpty()) {
                    Result.success(it)
                } else {
                    Result.failure(Throwable("Empty list found"))
                }
            }
            .catch {
                emit(Result.failure(it))
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() = apis.values.map {
        it.getRadioList()
    }
        .flatten()
        .let {
            flowOf(it)
        }
        .map {
            if (it.isNotEmpty()) {
                Result.success(it)
            } else {
                Result.success(radioChannelDao.getAll())
            }
        }
        .retry(2)
        .catch {
            emit(Result.success(radioChannelDao.getAll()))
        }

}