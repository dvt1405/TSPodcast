package tss.t.shared.search

import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.usecase.GetTrendingPodcasts
import tss.t.shared.usecase.SearchMusicFeeds
import tss.t.shared.usecase.SearchPodcasts
import tss.t.shared.util.EpochClock

/** The two chips of the Search tab (Android `SearchMode`). */
enum class SearchMode { All, Music }

/**
 * The calls behind the Search tab, like Android's `SearchViewModel.performSearch`
 * (features-spec §3.9), plus the category list that Android loads but never shows.
 *
 * Search makes exactly one request (no retry, like Android) and is not cached. Results are
 * blacklist-filtered by the use cases on `feed.id` and `title`. D6, unless
 * [AppPreferences.allowExplicitContent]: the request carries `clean=true` and feeds with
 * `explicit == true` are dropped after the blacklist. With D6 off no `clean` parameter is sent
 * at all: PodcastIndex treats `clean` as a presence flag, so `clean=false` would still filter.
 */
class SearchFeed internal constructor(
    private val searchPodcasts: SearchPodcasts,
    private val searchMusicFeeds: SearchMusicFeeds,
    private val getTrendingPodcasts: GetTrendingPodcasts,
    private val preferences: AppPreferences,
    private val clock: EpochClock,
) {
    /**
     * [query] is trimmed; a blank query returns `Success(emptyList())` without a request
     * ([Sửa]: Android sent `q=""`). [SearchMode.All] is E7 `search/byterm` (`max=100`,
     * `similar`, `fulltext`, `aponly=false`); [SearchMode.Music] is E8 `search/music/byterm`
     * (`val=music`, `max=60`, `similar`, `fulltext`).
     */
    suspend fun search(query: String, mode: SearchMode): TSDataState<List<Feed>> {
        val term = query.trim()
        if (term.isEmpty()) return TSDataState.Success(emptyList())

        val allowExplicit = preferences.allowExplicitContent()
        val clean = if (allowExplicit) null else true
        val result = when (mode) {
            SearchMode.All -> searchPodcasts(
                query = term,
                max = MAX_ALL,
                aponly = false,
                clean = clean,
                similar = true,
                fulltext = true,
            )

            SearchMode.Music -> searchMusicFeeds(
                query = term,
                max = MAX_MUSIC,
                clean = clean,
                similar = true,
                fulltext = true,
            )
        }
        return result.toFeeds(allowExplicit)
    }

    /**
     * Podcasts trending in one category over the last 7 days (features-spec §3.9 [Sửa]: Android
     * loads the categories but has no screen for them): E1 `podcasts/trending` with `max=100`,
     * `since = now − 7 days`, `lang=vi`, `cat=<categoryId>`, no `notcat`. [GetTrendingPodcasts]
     * filters the blacklist and caches the result for 15 minutes in memory and on disk
     * (`cachedTrending_<categoryId>`). D6 drops `explicit == 1`, although E1 does not send the
     * field today, so nothing is dropped in practice.
     */
    suspend fun categoryPodcasts(categoryId: Int): TSDataState<List<Podcast>> {
        val nowSeconds = clock.nowMillis() / 1_000
        val result = getTrendingPodcasts(
            max = MAX_CATEGORY,
            since = nowSeconds - CATEGORY_WINDOW_SECONDS,
            lang = LANG,
            cat = categoryId.toString(),
            notcat = null,
        )
        val allowExplicit = preferences.allowExplicitContent()
        return when (result) {
            is TSDataState.Success -> TSDataState.Success(result.data.podcasts(allowExplicit))
            is TSDataState.Error -> TSDataState.Error(result.exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    private fun TSDataState<SearchResponse>.toFeeds(allowExplicit: Boolean): TSDataState<List<Feed>> =
        when (this) {
            is TSDataState.Success -> TSDataState.Success(
                if (allowExplicit) data.feeds else data.feeds.filterNot { it.explicit },
            )

            is TSDataState.Error -> TSDataState.Error(exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }

    private fun TrendingPodcastRes.podcasts(allowExplicit: Boolean): List<Podcast> =
        if (allowExplicit) items else items.filter { it.explicit != EXPLICIT }

    companion object {
        const val MAX_ALL = 100
        const val MAX_MUSIC = SearchMusicFeeds.MAX_RESULTS
        const val MAX_CATEGORY = 100
        const val LANG = "vi"

        /** `now − 7 days`, in seconds. */
        const val CATEGORY_WINDOW_SECONDS = 7L * 24 * 60 * 60

        private const val EXPLICIT = 1
    }
}
