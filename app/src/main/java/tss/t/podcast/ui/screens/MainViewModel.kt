package tss.t.podcast.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import tss.t.coreapi.Constants
import tss.t.podcasts.usecase.GetTrendingPodcasts
import tss.t.podcasts.usecase.SearchPodcasts
import javax.inject.Inject


data class MainInteractors @Inject constructor(
    val getTrendingPodcasts: GetTrendingPodcasts,
    val searchPodcasts: SearchPodcasts
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val interactors: MainInteractors
) : ViewModel() {

    fun getTrending() {
        viewModelScope.launch {
            val result = interactors.getTrendingPodcasts(
                100,
                ((System.currentTimeMillis() - Constants.A_DAY * 2) / 1000).toInt()
            )
        }
    }
}