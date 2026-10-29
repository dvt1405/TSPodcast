package tss.t.coreradio.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.di.RadioRepo
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import tss.t.sharedlibrary.crash.safeCallOr
import javax.inject.Inject

class GetRadioList @Inject constructor(
    private val apis: Map<String, @JvmSuppressWildcards RadioApi>,
    private val radioChannelDao: RadioChannelDao
) {
    suspend operator fun invoke(
        repoKey: String
    ): Flow<Result<List<RadioChannel>>> {
        // assert() is disabled at runtime on Android (-ea is off), so the
        // following !! was the real behaviour: an NPE for any unknown key.
        val repo = apis[repoKey]
            ?: return flowOf(
                Result.failure(
                    IllegalStateException("No RadioApi bound for key: " + repoKey)
                )
            )
        return flowOf(repo.getRadioList())
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

    // Each source is fetched on its own: one site changing its markup used to
    // throw out of this map and blank the whole Radio tab, the other source too.
    suspend operator fun invoke() = apis.entries.map { (key, api) ->
        safeCallOr(TAG, emptyList(), mapOf("radio_repo" to key)) {
            api.getRadioList()
        }
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

    companion object {
        private const val TAG = "GetRadioList"
    }
}
