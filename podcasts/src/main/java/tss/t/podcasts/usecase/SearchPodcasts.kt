package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.BlacklistRepositoryImpl
import javax.inject.Inject

class SearchPodcasts @Inject constructor(
    private val repository: IPodcastRepository,
    private val blacklist: BlacklistRepositoryImpl
) {
    suspend operator fun invoke(
        query: String,
        type: String? = null,
        max: Int = 100,
        aponly: Boolean = false,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true,
        pretty: Boolean = false
    ): TSDataState<SearchResponse> {
        return repository.searchPodcasts(
            query = query,
            type = type,
            max = max,
            aponly = aponly,
            clean = clean,
            similar = similar,
            fulltext = fulltext,
            pretty = pretty
        ).let {
            if (it is TSDataState.Success) {
                val list = it.data.feeds.filter {
                    !(blacklist.isInBlacklist(it.id.toString())
                            || blacklist.isContainKeywordsBlacklist(it.title))
                }
                val data = it.data.copy(
                    count = list.size,
                    feeds = list
                )
                TSDataState.Success(data)
            }
            it
        }
    }
}