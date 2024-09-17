package tss.t.podcast.ui.screens.podcastsdetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.TrendingPodcast
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

    fun getEpisodes(podcastId: String) {
        _uiState.update {
            PodcastUIState.Loading.apply {
                this.renderCount = ++_renderCount
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcastId).zip(
                interactors.getPodcastByFeedID(podcastId)
            ) { rs1, rs2 ->
                Log.d("TuanDv", "getEpisodesRs1: $rs1")
                Log.d("TuanDv", "getEpisodesRs2: $rs2")
                if (rs1 is TSDataState.Success) {
                    PodcastUIState.Success(
                        data = rs1.data.items,
                        liveItems = rs1.data.liveItems ?: emptyList()
                    ).apply {
                        this.renderCount = ++_renderCount
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
            PodcastUIState.Init
        }
    }

    sealed class PodcastUIState(
        var renderCount: Int = 0,
        var podcast: TrendingPodcast? = null
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