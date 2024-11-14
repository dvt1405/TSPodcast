package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.TSDataState
import javax.inject.Inject

class SearchPodcasts @Inject constructor(
    private val repository: IPodcastRepository
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
        )
    }
}