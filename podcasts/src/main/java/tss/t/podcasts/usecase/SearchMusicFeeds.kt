package tss.t.podcasts.usecase

import tss.t.core.repository.IPodcastRepository
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.BlacklistRepositoryImpl
import javax.inject.Inject

/**
 * Searches feeds published with `podcast:medium = music`.
 *
 * These are ordinary RSS feeds whose items are tracks rather than episodes, so
 * everything downstream - the detail screen, the player, `Episode.toMediaItem()`
 * - already handles them without changes. No new network surface, no new
 * dependency, and no third-party terms to comply with beyond the ones the app
 * already operates under.
 *
 * The catalogue is small and has very little Vietnamese material; this is the
 * zero-risk slice, not a replacement for a licensed music source.
 */
class SearchMusicFeeds @Inject constructor(
    private val repository: IPodcastRepository,
    private val blacklist: BlacklistRepositoryImpl
) {
    suspend operator fun invoke(
        query: String,
        max: Int = MAX_RESULTS,
        clean: Boolean? = null,
        similar: Boolean? = true,
        fulltext: Boolean? = true
    ): TSDataState<SearchResponse> {
        if (query.isBlank()) {
            return TSDataState.Success(
                SearchResponse(count = 0, feeds = emptyList(), query = query)
            )
        }
        return repository.searchMusicPodcasts(
            query = query,
            type = TYPE_MUSIC,
            max = max,
            clean = clean,
            similar = similar,
            fulltext = fulltext
        ).let { state ->
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

    companion object {
        /** `val` query parameter required by search/music/byterm. */
        private const val TYPE_MUSIC = "music"
        private const val MAX_RESULTS = 60
    }
}
