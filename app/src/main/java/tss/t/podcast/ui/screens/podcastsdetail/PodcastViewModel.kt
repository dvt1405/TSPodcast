package tss.t.podcast.ui.screens.podcastsdetail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
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
import tss.t.coreapi.models.Podcast
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.TSDataState
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

    private val _uiState by lazy {
        MutableStateFlow<PodcastUIState>(PodcastUIState.Init)
    }

    val uiState: StateFlow<PodcastUIState>
        get() = _uiState

    fun setPodcastAndEpisodes(podcast: Podcast, playList: List<Episode>) {
        _uiState.update {
            val renderItemList: List<Any>
            if (it.podcast?.id != podcast.id) {
                renderItemList = generateItemList(playList)
            } else {
                renderItemList = (it as? PodcastUIState.Success)?.listRenderItems
                    ?: generateItemList(playList)
            }
            PodcastUIState.Success(
                episodes = playList,
                liveItems = emptyList(),
                listRenderItems = renderItemList
            ).apply {
                this.lazyListState = _uiState.value.lazyListState
                this.podcast = podcast
            }
        }
    }

    /**
     * Render list for the detail screen.
     *
     * This used to interleave AppLovin native-ad loaders at pseudo-random
     * positions, which is why the list is typed `Any`. With the ad SDK removed
     * it contains only episodes; the type is kept so the screen's existing
     * safe-cast rendering keeps working and a future ad network can slot back
     * in here alone.
     */
    private fun generateItemList(playList: List<Episode>): MutableList<Any> {
        return playList.toMutableList()
    }

    fun getEpisodes(podcast: Podcast) {
        if (_uiState.value is PodcastUIState.Success) {
            val data = (_uiState.value as PodcastUIState.Success).episodes
            if (data.isNotEmpty() && _uiState.value.podcast?.id == podcast.id) {
                return
            }
        }
        _uiState.update {
            PodcastUIState.Loading.apply {
                this.lazyListState = it.lazyListState
                this.podcast = podcast
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcast.id.toString()).zip(
                interactors.getPodcastByFeedID(podcast.id.toString())
            ) { rs1: TSDataState<EpisodeResponse>, rs2: TSDataState<PodcastByFeedIdRes> ->
                if (rs1 is TSDataState.Success) {
                    PodcastUIState.Success(
                        episodes = rs1.data.items,
                        liveItems = rs1.data.liveItems ?: emptyList(),
                        listRenderItems = generateItemList(rs1.data.items)
                    ).apply {
                        this.lazyListState = _uiState.value.lazyListState
                        this.podcast = podcast
                    }
                } else {
                    PodcastUIState.Error(
                        exception = rs1.exception()
                    ).apply {
                        this.lazyListState = _uiState.value.lazyListState
                        this.podcast = podcast
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
        _uiState.update {
            val newState = it
            newState.lazyListState = lazyListState
            newState.podcast = it.podcast
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

    override fun onCleared() {
        super.onCleared()
    }

    fun clearTempListState() {
        _uiState.update {
            it.lazyListState = null
            it
        }
    }

    @Immutable
    sealed class PodcastUIState(
        var podcast: Podcast? = null,
        var lazyListState: LazyListState? = null
    ) {
        @Immutable
        data object Init : PodcastUIState()

        @Immutable
        data object Loading : PodcastUIState()

        @Immutable
        data class Success(
            val episodes: List<Episode>,
            val liveItems: List<LiveEpisode>,
            val listRenderItems: List<Any>
        ) : PodcastUIState()

        @Immutable
        data class Error(val exception: Throwable) : PodcastUIState()
    }


}