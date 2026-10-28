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
        ).let { state ->
            // The blacklist result used to be computed and then dropped: the
            // `if` block built a filtered TSDataState.Success but the last
            // expression of this block was a bare `it`, so the unfiltered
            // response was returned. Search was the only content surface not
            // actually filtered.
            if (state !is TSDataState.Success) return@let state
            val list = state.data.feeds.filter { feed ->
                !(blacklist.isInBlacklist(feed.id.toString())
                        || blacklist.isContainKeywordsBlacklist(feed.title))
            }
            TSDataState.Success(
                state.data.copy(
                    count = list.size,
                    feeds = list
                )
            )
        }
    }
}