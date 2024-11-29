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
import tss.t.ads.MaxTemplateNativeAdViewComposableLoader
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.EpisodeResponse
import tss.t.coreapi.models.LiveEpisode
import tss.t.coreapi.models.PodcastByFeedIdRes
import tss.t.coreapi.models.TSDataState
import tss.t.coreapi.models.Podcast
import tss.t.podcast.App
import tss.t.podcasts.usecase.GetEpisodeByFeedId
import tss.t.podcasts.usecase.GetPodcastByFeedID
import tss.t.securedtoken.NativeLib
import javax.inject.Inject
import kotlin.random.Random

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
            val renderItemList: List<Any>
            if (it.podcast?.id != podcast.id) {
                (it as? PodcastUIState.Success)?.listRenderItems?.forEach {
                    (it as? MaxTemplateNativeAdViewComposableLoader)?.destroy()
                }
                renderItemList = generateItemList(playList)
            } else {
                renderItemList = (it as? PodcastUIState.Success)?.listRenderItems
                    ?: generateItemList(playList)
            }
            PodcastUIState.Success(
                data = playList,
                liveItems = emptyList(),
                listRenderItems = renderItemList
            ).apply {
                this.renderCount = ++_renderCount
                this.lazyListState = _uiState.value.lazyListState
                this.podcast = podcast
            }
        }
    }

    private fun generateItemList(playList: List<Episode>): MutableList<Any> {
        val adsList = mutableListOf<Int>()
        val renderItemList = mutableListOf<Any>()
        for (i in 0..(playList.size / 8).coerceAtLeast(1)) {
            val maxRandom = ((i + 1) * 8 + 1).coerceAtMost(playList.size)
            var nextInt = Random.nextInt(i * 8 + 1, maxRandom)
            while (adsList.contains(nextInt)) {
                nextInt = Random.nextInt(i * 8 + 1, maxRandom)
            }
            adsList.add(nextInt)
        }

        for (i in playList.indices) {
            renderItemList.add(playList[i])
            if (i in adsList) {
                val randomAd = Random.nextInt(0, 2)
                val adId = if (randomAd == 0) {
                    NativeLib.getNativeMediumId()
                } else {
                    NativeLib.getNativeSmallId()
                }
                renderItemList.add(
                    MaxTemplateNativeAdViewComposableLoader(
                        adUnitIdentifier = adId,
                        context = App.instance
                    )
                )
            }
        }
        return renderItemList
    }

    fun getEpisodes(podcast: Podcast) {
        (_uiState.value as? PodcastUIState.Success)?.listRenderItems?.forEach {
            if (it is MaxTemplateNativeAdViewComposableLoader) {
                it.destroy()
            }
        }
        _uiState.update {
            PodcastUIState.Loading.apply {
                this.renderCount = ++_renderCount
                this.lazyListState = it.lazyListState
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            interactors.getEpisodeByFeedId(podcast.id.toString()).zip(
                interactors.getPodcastByFeedID(podcast.id.toString())
            ) { rs1: TSDataState<EpisodeResponse>, rs2: TSDataState<PodcastByFeedIdRes> ->
                if (rs1 is TSDataState.Success) {
                    PodcastUIState.Success(
                        data = rs1.data.items,
                        liveItems = rs1.data.liveItems ?: emptyList(),
                        listRenderItems = generateItemList(rs1.data.items)
                    ).apply {
                        this.renderCount = ++_renderCount
                        this.lazyListState = _uiState.value.lazyListState
                        this.podcast = podcast
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

    override fun onCleared() {
        super.onCleared()
        (_uiState.value as? PodcastUIState.Success)?.listRenderItems?.forEach {
            if (it is MaxTemplateNativeAdViewComposableLoader) {
                it.destroy()
            }
        }
    }

    @Immutable
    sealed class PodcastUIState(
        var renderCount: Int = 0,
        var podcast: Podcast? = null,
        var lazyListState: LazyListState? = null
    ) {
        @Immutable
        data object Init : PodcastUIState()

        @Immutable
        data object Loading : PodcastUIState()

        @Immutable
        data class Success(
            val data: List<Episode>,
            val liveItems: List<LiveEpisode>,
            val listRenderItems: List<Any>
        ) : PodcastUIState()

        @Immutable
        data class Error(val exception: Throwable) : PodcastUIState()
    }


}