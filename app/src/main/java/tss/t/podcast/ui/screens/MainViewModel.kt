package tss.t.podcast.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getFavouriteCategory
import tss.t.coreapi.Constants
import tss.t.coreapi.models.BaseResponse
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcast.ui.model.HomeEvent
import tss.t.podcast.ui.navigations.TSNavigators
import tss.t.podcasts.usecase.GetEpisodeByFeedId
import tss.t.podcasts.usecase.GetLiveEpisodes
import tss.t.podcasts.usecase.GetPodcastByFeedID
import tss.t.podcasts.usecase.GetRecentEpisodes
import tss.t.podcasts.usecase.GetRecentFeeds
import tss.t.podcasts.usecase.GetRecentNewFeeds
import tss.t.podcasts.usecase.GetTrendingPodcasts
import tss.t.podcasts.usecase.SearchPodcasts
import javax.inject.Inject


data class MainInteractors @Inject constructor(
    val getTrendingPodcasts: GetTrendingPodcasts,
    val searchPodcasts: SearchPodcasts,
    val getPodcastByFeedID: GetPodcastByFeedID,
    val getEpisodeByFeedId: GetEpisodeByFeedId,
    val getLiveEpisodes: GetLiveEpisodes,
    val getRecentEpisodes: GetRecentEpisodes,
    val getRecentNewFeeds: GetRecentNewFeeds,
    val getRecentFeeds: GetRecentFeeds,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactors: MainInteractors,
    private val sharedPref: SharedPref
) : ViewModel(), TSNavigators.Companion.Observer {
    private var renderCount = 0
    private val _event by lazy {
        MutableSharedFlow<HomeEvent>(
            1,
            1
        )
    }
    val event: SharedFlow<HomeEvent>
        get() = _event.asSharedFlow()

    private val _uiState by lazy {
        MutableStateFlow(
            UIState(
                renderCount = renderCount,
                isDataPartLoading = mutableMapOf(
                    *HomepageDataPart.entries.map {
                        it.value to true
                    }.toTypedArray()
                ),
                showLoadingView = true
            )
        )
    }

    val uiState: StateFlow<UIState>
        get() = _uiState.asStateFlow()

    init {
        getTrending()
        TSNavigators.add(this)
    }

    fun reload() {
        val isLoading = _uiState.value.isDataPartLoading
        synchronized(isLoading) {
            HomepageDataPart.entries.forEach {
                isLoading[it.value] = true
            }
        }
        _uiState.update {
            it.copy(
                renderCount = ++renderCount,
                isDataPartLoading = isLoading,
                showLoadingView = true
            )
        }
        getTrending()
    }

    private fun getTrending() {
        val listFavCat = sharedPref.getFavouriteCategory() ?: setOf()
        val catId = listFavCat
            .map { it.id }
            .joinToString(",")

        viewModelScope.launch(Dispatchers.IO) {
            merge(
                interactors.getLiveEpisodes(),
                interactors.getRecentEpisodes(
                    since = ((System.currentTimeMillis() - Constants.A_DAY * 10) / 1000),
                ),
                interactors.getRecentNewFeeds(
                    since = ((System.currentTimeMillis() - Constants.A_DAY * 10) / 1000),
                ),
                interactors.getRecentFeeds(
                    cat = catId,
                    notcat = null,
                )
            ).collect { rs ->
                if (rs is TSDataState.Success) {
                    val data = rs.data
                    updateData(data)
                } else if (rs is TSDataState.Error) {
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            flowOf(
                interactors.getTrendingPodcasts(
                    100,
                    ((System.currentTimeMillis() - Constants.A_DAY * 7) / 1000).toInt()
                )
            ).zip(
                flowOf(
                    interactors.getTrendingPodcasts(
                        max = 100,
                        since = ((System.currentTimeMillis() - Constants.A_DAY * 10) / 1000).toInt(),
                        cat = catId
                    )
                ),
            ) { trending, favourite ->
                if (trending is TSDataState.Success) {
                    if (favourite is TSDataState.Success) {
                        _uiState.value.copy(
                            renderCount = ++renderCount,
                            listFav = favourite.data.items,
                            listTrending = trending.data.items,
                            error = null,
                            showLoadingView = false
                        )
                    } else {
                        _uiState.value.copy(
                            renderCount = ++renderCount,
                            listFav = trending.data.items,
                            listTrending = trending.data.items,
                            error = null,
                            showLoadingView = false
                        )
                    }
                } else if (trending is TSDataState.Error) {
                    if (favourite is TSDataState.Success) {
                        _uiState.value.copy(
                            renderCount = ++renderCount,
                            listFav = favourite.data.items,
                            listTrending = favourite.data.items,
                            error = null,
                            showLoadingView = false
                        )
                    } else {
                        _uiState.value.copy(
                            renderCount = ++renderCount,
                            listFav = emptyList(),
                            listTrending = emptyList(),
                            error = trending.exception,
                            showLoadingView = false
                        )
                    }
                } else {
                    _uiState.value.copy(
                        renderCount = ++renderCount,
                        listFav = emptyList(),
                        listTrending = emptyList(),
                        error = trending.exception(),
                        showLoadingView = false
                    )
                }
            }.collectLatest { ui ->
                synchronized(ui.isDataPartLoading) {
                    ui.isDataPartLoading[HomepageDataPart.Trending.value] = false
                    ui.isDataPartLoading[HomepageDataPart.Favourite.value] = false
                    _uiState.update { ui }
                }
            }
        }
    }

    private fun updateData(data: BaseResponse) {
        val isLoading = _uiState.value.isDataPartLoading
        when (data) {
            is LiveResponse -> {
                isLoading[HomepageDataPart.LiveEpisode.value] = false
                _uiState.update {
                    it.copy(
                        liveEpisode = data.items.chunked(3),
                        renderCount = ++renderCount,
                        isDataPartLoading = isLoading,
                        showLoadingView = false
                    )
                }
            }

            is EpisodeResponse -> {

            }

            is TrendingPodcastRes -> {
                val type = data.type
                if (type == TrendingPodcastRes.Type.RecentNewFeed) {
                    isLoading[HomepageDataPart.RecentNewFeeds.value] = false
                    _uiState.update {
                        it.copy(
                            recentNewFeeds = data.items,
                            renderCount = ++renderCount,
                            isDataPartLoading = isLoading,
                            showLoadingView = false
                        )
                    }
                } else if (type == TrendingPodcastRes.Type.RecentFeed) {
                    isLoading[HomepageDataPart.RecentFeed.value] = false
                    _uiState.update {
                        it.copy(
                            recentFeeds = data.items,
                            renderCount = ++renderCount,
                            isDataPartLoading = isLoading,
                            showLoadingView = false
                        )
                    }
                }
            }
        }
    }

    private val _tabSelected by lazy {
        MutableStateFlow<Int>(1)
    }
    val tabSelected: StateFlow<Int>
        get() = _tabSelected.asStateFlow()

    fun onTabSelected(tab: Int) {
        _tabSelected.update {
            tab
        }
    }

    fun setCurrentPodcast(
        podcast: Podcast,
        from: String? = null
    ) {
        _uiState.update {
            it.copy(
                currentPodcast = podcast,
                from = from
            )
        }
    }

    fun onErrorDialogDismiss() {
        _uiState.update {
            it.copy(error = null, renderCount = ++renderCount)
        }
    }

    fun popBackStack() {
        if (TSNavigators.isRoot) {
            doubleBackToExit()
        } else {
            TSNavigators.popBack()
        }
    }

    private var _pendingExitApp = false
    private var _doubleBackCount = 0
    private fun doubleBackToExit() {
        viewModelScope.launch {
            if (!_pendingExitApp) {
                _pendingExitApp = true
                _doubleBackCount++
                _event.emit(HomeEvent.ToastDoubleClickToExit)
                delay(2_000L)
                _pendingExitApp = false
                _doubleBackCount = 0
                return@launch
            }
            _doubleBackCount++
            if (_doubleBackCount >= 2) {
                _event.emit(HomeEvent.ExitApp)
            }
        }
    }

    private fun exitApp() {

    }

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
        val route: TSNavigators? = null,
        val episode: Episode? = null,
        val playList: List<Episode> = emptyList()
    )

    enum class HomepageDataPart(val value: Int) {
        Trending(0),
        Favourite(1),
        LiveEpisode(2),
        RecentFeed(3),
        RecentNewFeeds(4),
        RecentEpisode(5)
    }

    override fun onCleared() {
        super.onCleared()
        TSNavigators.remove(this)
    }

    override fun onChanged(route: TSNavigators?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    route = route,
                )
            }
        }
    }

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