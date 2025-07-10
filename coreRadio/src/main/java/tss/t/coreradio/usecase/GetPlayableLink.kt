package tss.t.coreradio.usecase

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.di.RadioRepo
import tss.t.coreradio.models.RadioChannel
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class GetPlayableLink @Inject constructor(
    private val apis: Map<String, @JvmSuppressWildcards RadioApi>,
    @Named(RadioRepo.VOV)
    private val vovRepo: RadioApi,
) {
    suspend operator fun invoke(radioChannel: RadioChannel) =
        (
                if (radioChannel.category.equals(RadioRepo.VOV, ignoreCase = true))
                    flowOf(apis[RadioRepo.VOV]!!)
                else flowOf(apis[RadioRepo.VOH]!!)
                )
            .map {
                it.getPlayableLink(radioChannel = radioChannel)
            }
            .retry(2)
            .map {
                if (it.link.isNotEmpty()) {
                    Result.success(it)
                } else {
                    Result.failure(Throwable("Playable link not found"))
                }
            }
            .catch {
                emit(Result.failure(it))
            }

    suspend operator fun invoke(link: String) = apis[RadioRepo.VOV]!!.getPlayableLink(link)
    suspend operator fun invoke(
        link: String,
        category: String,
    ) = runCatching {
        getRepo(category)
            .getPlayableLink(link)
    }

    private fun getRepo(category: String): RadioApi {
        return if (category.equals(RadioRepo.VOH, ignoreCase = true)) {
            apis[RadioRepo.VOH]!!
        } else {
            apis[RadioRepo.VOV]!!
        }
    }

    companion object {
        private val _cache by lazy {
        }
    }
}