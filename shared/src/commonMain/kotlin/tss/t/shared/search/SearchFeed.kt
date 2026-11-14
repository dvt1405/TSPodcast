package tss.t.shared.search

import tss.t.shared.blacklist.HiddenShowsRepository
import tss.t.shared.blacklist.withoutHidden
import tss.t.shared.blacklist.withoutHiddenFeeds
import tss.t.shared.location.LanguageMatch
import tss.t.shared.location.PodcastLocationRepository
import tss.t.shared.model.Feed
import tss.t.shared.model.Podcast
import tss.t.shared.model.SearchResponse
import tss.t.shared.model.TSDataState
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.usecase.GetPodcastsByLocation
import tss.t.shared.usecase.SearchMusicFeeds
import tss.t.shared.usecase.SearchPodcasts

/** The two chips of the Search tab (Android `SearchMode`). */
enum class SearchMode { All, Music }

/**
 * The calls behind the Search tab, like Android's `SearchViewModel.performSearch`
 * (features-spec §3.9), plus the topic page (trending shows of a category).
 *
 * Search makes exactly one request (no retry, like Android) and is not cached. Results are
 * blacklist-filtered by the use cases on `feed.id` and `title`. D6, unless
 * [AppPreferences.allowExplicitContent]: the request carries `clean=true` and feeds with
 * `explicit == true` are dropped after the blacklist. With D6 off no `clean` parameter is sent
 * at all: PodcastIndex treats `clean` as a presence flag, so `clean=false` would still filter.
 * Then the shows the user hid ([HiddenShowsRepository], M6 §3.5) are dropped. Last, podcast
 * search ([SearchMode.All], not music) keeps only shows in the languages of the chosen podcast
 * locations, ranked by location priority ([inLanguages], Android `PodcastsRepositoryImpl.filtered`).
 */
class SearchFeed internal constructor(
    private val searchPodcasts: SearchPodcasts,
    private val searchMusicFeeds: SearchMusicFeeds,
    private val getPodcastsByLocation: GetPodcastsByLocation,
    private val preferences: AppPreferences,
    private val hiddenShows: HiddenShowsRepository,
    private val locations: PodcastLocationRepository,
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
        val feeds = result.toFeeds(allowExplicit, hiddenShows.hiddenIds())
        if (mode == SearchMode.Music || feeds !is TSDataState.Success) return feeds
        return TSDataState.Success(inLanguages(feeds.data, locations.contentCodes()))
    }

    /**
     * The topic page: shows trending in [categoryId] in the primary podcast location (Android
     * `SearchViewModel.selectCategory`): [GetPodcastsByLocation] with `cat=<categoryId>`, 60
     * shows of the last 30 days, its 15-minute cache, the blacklist and the title filter. D6 drops
     * `explicit == 1` (E1 does not send the field today), then hidden shows are dropped.
     */
    suspend fun categoryPodcasts(categoryId: Int): TSDataState<List<Podcast>> {
        val result = getPodcastsByLocation(
            locations.primary(),
            cat = categoryId.toString(),
            max = GetPodcastsByLocation.CATEGORY_MAX,
            sinceDays = GetPodcastsByLocation.CATEGORY_SINCE_DAYS,
        )
        val allowExplicit = preferences.allowExplicitContent()
        return when (result) {
            is TSDataState.Success -> TSDataState.Success(
                (if (allowExplicit) result.data else result.data.filter { it.explicit != EXPLICIT })
                    .withoutHidden(hiddenShows.hiddenIds()),
            )
            is TSDataState.Error -> TSDataState.Error(result.exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }
    }

    private fun TSDataState<SearchResponse>.toFeeds(
        allowExplicit: Boolean,
        hidden: Set<Long>,
    ): TSDataState<List<Feed>> =
        when (this) {
            is TSDataState.Success -> TSDataState.Success(
                (if (allowExplicit) data.feeds else data.feeds.filterNot { it.explicit }).withoutHiddenFeeds(hidden),
            )

            is TSDataState.Error -> TSDataState.Error(exception)
            is TSDataState.Loading -> TSDataState.Loading()
        }

    companion object {
        const val MAX_ALL = 100
        const val MAX_MUSIC = SearchMusicFeeds.MAX_RESULTS

        private const val EXPLICIT = 1

        /**
         * Only feeds whose language ([LanguageMatch.code] of `feed.language`) is one of [codes],
         * ranked by its position there (location priority; stable within a language), and whose
         * title fits that language ([LanguageMatch.titleFits]). A feed without a language is
         * dropped, as on Android.
         */
        fun inLanguages(feeds: List<Feed>, codes: List<String>): List<Feed> =
            feeds
                .mapNotNull { feed ->
                    val code = LanguageMatch.code(feed.language)
                    val rank = codes.indexOf(code)
                    if (rank < 0 || !LanguageMatch.titleFits(feed.title, code)) null else rank to feed
                }
                .sortedBy { it.first }
                .map { it.second }
    }
}
