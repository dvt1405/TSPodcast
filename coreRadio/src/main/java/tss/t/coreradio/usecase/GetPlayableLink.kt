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
        flowOf(getRepo(radioChannel.category))
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

    suspend operator fun invoke(link: String) = getRepo(RadioRepo.VOV).getPlayableLink(link)
    suspend operator fun invoke(
        link: String,
        category: String,
    ) = runCatching {
        getRepo(category)
            .getPlayableLink(link)
    }

    /**
     * Resolves a source from the Dagger multibinding. Previously `apis[key]!!`,
     * which NPEs if a key is renamed or a channel carries an unknown source.
     * Falls back to the VOV binding, which is also bound under @Named and so is
     * always available.
     */
    private fun getRepo(category: String): RadioApi {
        val key = if (category.equals(RadioRepo.VOH, ignoreCase = true)) {
            RadioRepo.VOH
        } else {
            RadioRepo.VOV
        }
        return apis[key] ?: apis[RadioRepo.VOV] ?: vovRepo
    }

    companion object {
        private val _cache by lazy {
        }
    }
}