package tss.t.podcast.ui.screens.uimodels.main

import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.Podcast

data class UIState(
    val renderCount: Int = 0,
    val listTrending: List<Podcast> = emptyList(),
    val listFav: List<Podcast> = emptyList(),
    val liveEpisode: List<List<LiveEpisode>> = _dumpList,
    val recentFeeds: List<Podcast> = emptyList(),
    val recentNewFeeds: List<Podcast> = emptyList(),
    /** Feeds with podcast:medium = music. Unordered - the API offers no ranking. */
    val musicFeeds: List<Podcast> = emptyList(),
    /** "Thời sự & Bản tin": empty (section hidden) until enough confirmed news sources resolve. */
    val newsItems: List<tss.t.podcasts.NewsItem> = emptyList(),
    /** "Thời sự" from the catalog (News shows of the primary location), shown when the hub is empty. */
    val catalogNews: List<tss.t.podcasts.NewsItem> = emptyList(),
    /** Podcast locations in priority order (at most 3). */
    val locations: List<tss.t.core.storage.PodcastLocation> = emptyList(),
    /** Trending shows per location region; a region missing here is still loading. */
    val locationPodcasts: Map<String, List<Podcast>> = emptyMap(),
    val isDataPartLoading: MutableMap<Int, Boolean> = mutableMapOf(),
    val showLoadingView: Boolean = true,
    val error: Throwable? = null,
    val currentPodcast: Podcast? = null,
    val from: String? = null,
    val episode: Episode? = null,
    val playList: List<Episode> = emptyList()
) {
    companion object {
        private val _dumpList by lazy {
            listOf(
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
                LiveEpisode.default,
            ).chunked(3)
        }
    }
}