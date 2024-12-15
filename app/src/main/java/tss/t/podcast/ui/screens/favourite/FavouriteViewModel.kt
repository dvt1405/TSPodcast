package tss.t.podcast.ui.screens.favourite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.core.models.FavouriteDTO
import tss.t.coreapi.models.Podcast
import tss.t.podcasts.usecase.favourite.SelectAllFavourite
import tss.t.podcasts.usecase.favourite.SelectPodcastAndEpisodeByFavourite
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    private val _selectAllFavourite: SelectAllFavourite,
    private val _selectPodcastAndEpisodeByFavourite: SelectPodcastAndEpisodeByFavourite
) : ViewModel() {

    init {
        selectAll()
    }

    private val _favUIState by lazy {
        MutableStateFlow(FavouriteUIState())
    }

    val favUiState: StateFlow<FavouriteUIState>
        get() = _favUIState.asStateFlow()

    fun selectAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _selectAllFavourite.invoke()
            _favUIState.update {
                it.copy(
                    listFav = list,
                    isLoading = false
                )
            }
        }
    }

    suspend fun onFavSelected(fav: FavouriteDTO): Podcast? {
        return viewModelScope.async(Dispatchers.IO) {
            _selectPodcastAndEpisodeByFavourite.invoke(fav)
        }.await()?.podcast
    }

    data class FavouriteUIState(
        val listFav: List<FavouriteDTO> = emptyList(),
        val isLoading: Boolean = true
    )
}