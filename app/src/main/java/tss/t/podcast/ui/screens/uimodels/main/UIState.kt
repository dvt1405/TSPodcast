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