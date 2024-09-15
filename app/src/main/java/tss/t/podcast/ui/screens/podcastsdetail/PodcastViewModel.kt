package tss.t.podcast.ui.screens.podcastsdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    fun getPodcast(podcast: TrendingPodcast) {
        getEpisodes("${podcast.id}")
        viewModelScope.launch(Dispatchers.IO) {
            interactors.getPodcastByFeedID("${podcast.id}")
                .collect { result ->
//                    if (result is TSDataState.Success) {
//                        val data = result.data
//                        _uiState.update {
//                            it.copy(
//                                error = null,
//                                renderCount = ++renderCount,
//                            )
//                        }
//
//                    } else if (result is TSDataState.Error) {
//                        _uiState.update {
//                            it.copy(
//                                renderCount = ++renderCount,
//                                error = result.exception
//                            )
//                        }
//                    }
                }
        }
    }

    fun getEpisodes(podcastId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcastId)
                .collect { result ->
//                    if (result is TSDataState.Success) {
//                        _uiState.update {
//                            it.copy(
//                                error = null,
//                                renderCount = ++renderCount,
//                            )
//                        }
//
//                    } else if (result is TSDataState.Error) {
//                        _uiState.update {
//                            it.copy(
//                                renderCount = ++renderCount,
//                                error = result.exception
//                            )
//                        }
//                }
                }
        }
    }


}