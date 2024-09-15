package tss.t.podcast.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import tss.t.core.storage.SharedPref
import tss.t.core.storage.getFavouriteCategory
import tss.t.coreapi.Constants
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcast
import tss.t.podcasts.usecase.GetEpisodeByFeedId
import tss.t.podcasts.usecase.GetPodcastByFeedID
import tss.t.podcasts.usecase.GetTrendingPodcasts
import tss.t.podcasts.usecase.SearchPodcasts
import javax.inject.Inject


data class MainInteractors @Inject constructor(
    val getTrendingPodcasts: GetTrendingPodcasts,
    val searchPodcasts: SearchPodcasts,
    val getPodcastByFeedID: GetPodcastByFeedID,
    val getEpisodeByFeedId: GetEpisodeByFeedId
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactors: MainInteractors,
    private val sharedPref: SharedPref
) : ViewModel() {
    private var renderCount = 0
    private val _uiState by lazy {
        MutableStateFlow(
            UIState(
                renderCount = renderCount,
                isLoading = true
            )
        )
    }

    val uiState: StateFlow<UIState>
        get() = _uiState

    init {
        getTrending()
    }

    fun reload() {
        getTrending()
    }

    private fun getTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            val listFavCat = sharedPref.getFavouriteCategory() ?: setOf()
            val catId = listFavCat
                .map { it.id }
                .joinToString {
                    ","
                }

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
                )
            ) { trending, favourite ->
                if (trending is TSDataState.Success) {
                    if (favourite is TSDataState.Success) {
                        UIState(
                            renderCount = ++renderCount,
                            isLoading = false,
                            listFav = trending.data.items,
                            listTrending = favourite.data.items,
                            error = null
                        )
                    } else {
                        UIState(
                            renderCount = ++renderCount,
                            isLoading = false,
                            listFav = trending.data.items,
                            listTrending = trending.data.items,
                            error = null
                        )
                    }
                } else if (trending is TSDataState.Error) {
                    UIState(
                        renderCount = ++renderCount,
                        isLoading = false,
                        listFav = emptyList(),
                        listTrending = emptyList(),
                        error = trending.exception()
                    )
                } else {
                    UIState(
                        renderCount = ++renderCount,
                        isLoading = false,
                        listFav = emptyList(),
                        listTrending = emptyList(),
                        error = favourite.exception()
                    )
                }
            }.collectLatest { ui ->
                _uiState.update {
                    ui
                }
            }
        }
    }

    fun getFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            val listFavCat = sharedPref.getFavouriteCategory() ?: setOf()
            val catId = listFavCat
                .map { it.id }
                .joinToString {
                    ","
                }

            val result = interactors.getTrendingPodcasts(
                max = 100,
                since = ((System.currentTimeMillis() - Constants.A_DAY * 10) / 1000).toInt(),
                cat = catId
            )

            if (result is TSDataState.Success) {
                _uiState.update {
                    it.copy(
                        listFav = result.data.items,
                        isLoading = false,
                        renderCount = ++renderCount,
                        error = null
                    )
                }
            } else if (result is TSDataState.Error) {
                Log.e("TuanDv", "getFavorite: ${result.exception.message}", result.exception)
                _uiState.update {
                    it.copy(error = result.exception, renderCount = ++renderCount)
                }
            }
        }
    }

    fun getPodcast(podcast: TrendingPodcast) {
        getEpisodes("${podcast.id}")
        _uiState.update {
            it.copy(currentPodcast = podcast)
        }

        viewModelScope.launch(Dispatchers.IO) {
            interactors.getPodcastByFeedID("${podcast.id}")
                .collect { result ->
                    if (result is TSDataState.Success) {
                        val data = result.data
                        _uiState.update {
                            it.copy(
                                error = null,
                                renderCount = ++renderCount,
                            )
                        }

                    } else if (result is TSDataState.Error) {
                        _uiState.update {
                            it.copy(
                                renderCount = ++renderCount,
                                error = result.exception
                            )
                        }
                    }
                }
        }
    }

    fun getEpisodes(podcastId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcastId)
                .collect { result ->
                    if (result is TSDataState.Success) {
                        _uiState.update {
                            it.copy(
                                error = null,
                                renderCount = ++renderCount,
                            )
                        }

                    } else if (result is TSDataState.Error) {
                        _uiState.update {
                            it.copy(
                                renderCount = ++renderCount,
                                error = result.exception
                            )
                        }
                    }
                }
        }
    }

    fun onErrorDialogDismiss() {
        _uiState.update {
            it.copy(error = null, renderCount = ++renderCount)
        }
    }

    fun popBackStack() {
        _uiState.update {
            it.copy(currentPodcast = null)
        }
    }

    data class UIState(
        val renderCount: Int = 0,
        val listTrending: List<TrendingPodcast> = listOf(),
        val listFav: List<TrendingPodcast> = listOf(),
        val isLoading: Boolean = false,
        val error: Throwable? = null,
        val currentPodcast: TrendingPodcast? = null
    )
}