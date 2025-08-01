package tss.t.podcast.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.LiveResponse
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcastRes
import tss.t.podcast.ui.model.HomeEvent
import tss.t.podcast.ui.navigations.NavConstants
import tss.t.podcast.ui.screens.uimodels.main.HomepageDataPart
import tss.t.podcast.ui.screens.uimodels.main.UIState
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
    private val savedStateHandle: SavedStateHandle,
    private val interactors: MainInteractors,
    private val sharedPref: SharedPref,
) : ViewModel() {
    private val gson = Gson()
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

    private val _mapLazyListState by lazy {
        mutableMapOf<String, LazyListState>()
    }

    init {
        getTrending()
    }

    fun getLazyListState(key: String): LazyListState {
        var cached = synchronized(_mapLazyListState) {
            _mapLazyListState[key]
        }
        if (cached == null) {
            cached = LazyListState()
            synchronized(_mapLazyListState) {
                _mapLazyListState[key] = cached
            }
        }
        return cached
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

    fun setCurrentPodcast(
        podcast: Podcast,
        from: String? = null
    ) {
        savedStateHandle[NavConstants.KEY_PODCAST] = gson.toJson(podcast)
        _uiState.update {
            it.copy(
                currentPodcast = podcast,
                from = from
            )
        }
    }

    fun emitEvent(homeEvent: HomeEvent) {
        viewModelScope.launch {
            _event.emit(homeEvent)
        }
    }

    fun getCurrentPodcast(): Podcast? {
        return savedStateHandle.get<String?>(NavConstants.KEY_PODCAST)?.let {
            runCatching {
                gson.fromJson(it, Podcast::class.java)
            }.getOrNull()
        }
    }
}