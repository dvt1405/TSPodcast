package tss.t.podcast.ui.screens.podcastsdetail

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.Podcast
import tss.t.podcasts.usecase.GetEpisodeByFeedId
import tss.t.podcasts.usecase.GetPodcastByFeedID
import javax.inject.Inject

data class PodcastInteractors @Inject constructor(
    val getEpisodeByFeedId: GetEpisodeByFeedId,
    val getPodcastByFeedID: GetPodcastByFeedID
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val interactors: PodcastInteractors
) : ViewModel() {
    private var _renderCount: Int = 0

    private val _uiState by lazy {
        MutableStateFlow<PodcastUIState>(PodcastUIState.Init)
    }

    val uiState: StateFlow<PodcastUIState>
        get() = _uiState

    fun setPodcastAndEpisodes(podcast: Podcast, playList: List<Episode>) {
        _uiState.update {
            PodcastUIState.Success(
                data = playList,
                liveItems = emptyList()
            ).apply {
                this.renderCount = ++_renderCount
                this.lazyListState = _uiState.value.lazyListState
            }
        }
    }

    fun getEpisodes(podcastId: String) {
        _uiState.update {
            PodcastUIState.Loading.apply {
                this.renderCount = ++_renderCount
                this.lazyListState = it.lazyListState
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcastId).zip(
                interactors.getPodcastByFeedID(podcastId)
            ) { rs1: TSDataState<EpisodeResponse>, rs2: TSDataState<PodcastByFeedIdRes> ->
                if (rs1 is TSDataState.Success) {
                    PodcastUIState.Success(
                        data = rs1.data.items,
                        liveItems = rs1.data.liveItems ?: emptyList()
                    ).apply {
                        this.renderCount = ++_renderCount
                        this.lazyListState = _uiState.value.lazyListState
                    }
                } else {
                    PodcastUIState.Error(
                        exception = rs1.exception()
                    ).apply {
                        this.renderCount = ++_renderCount
                    }
                }
            }.collectLatest {
                _uiState.value = it
            }
        }
    }

    fun dismissDialog() {
        _uiState.update {
            PodcastUIState.Init.apply {
                this.lazyListState = it.lazyListState
            }
        }
    }

    fun initListState(lazyListState: LazyListState) {
        Log.d("TuanDv", "initListState: ")
        _uiState.update {
            val newState = it
            newState.lazyListState = lazyListState
            newState
        }
    }

    var firstIndex: Int? = null
    var firstOffset: Int? = null
    fun onSavedState() {
        firstIndex = _uiState.value.lazyListState?.firstVisibleItemIndex
        firstOffset = _uiState.value.lazyListState?.firstVisibleItemScrollOffset
    }

    fun onRestoreState() {
        val index = firstIndex ?: return
        val offset = firstOffset ?: return
        firstOffset ?: return
        viewModelScope.launch {
            delay(200)
            _uiState.value.lazyListState?.scrollToItem(index, offset)
            firstIndex = null
            firstOffset = null
        }
    }

    sealed class PodcastUIState(
        var renderCount: Int = 0,
        var podcast: Podcast? = null,
        var lazyListState: LazyListState? = null
    ) {
        data object Init : PodcastUIState()
        data object Loading : PodcastUIState()
        data class Success(
            val data: List<Episode>,
            val liveItems: List<LiveEpisode>
        ) : PodcastUIState()

        data class Error(val exception: Throwable) : PodcastUIState()
    }


}