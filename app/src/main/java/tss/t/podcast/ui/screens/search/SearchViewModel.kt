package tss.t.podcast.ui.screens.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.SearchResponse
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.usecase.GetCategories
import tss.t.podcasts.usecase.SearchMusicFeeds
import tss.t.podcasts.usecase.SearchPodcasts
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val _getCategory: GetCategories,
    private val _searchPodcasts: SearchPodcasts,
    private val _searchMusicFeeds: SearchMusicFeeds
) : ViewModel() {

    private val _searchMode by lazy { MutableStateFlow(SearchMode.All) }
    val searchMode: StateFlow<SearchMode>
        get() = _searchMode.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        if (_searchMode.value == mode) return
        _searchMode.value = mode
        // Re-run the current query through the other endpoint rather than
        // leaving the previous mode's results on screen.
        performSearch(currentSearchText.value)
    }

    private val _listCategory by lazy {
        MutableStateFlow<List<CategoryRes.Category>>(emptyList())
    }
    val listCategory: StateFlow<List<CategoryRes.Category>>
        get() = _listCategory.asStateFlow()
    var currentSearchText = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _getCategory.invoke(true)
                .collectLatest { list ->
                    if (list is TSDataState.Success) {
                        _listCategory.update {
                            list.data.feeds
                        }
                    }

                }
        }
    }

    private val _listSearch by lazy {
        MutableStateFlow<List<Feed>>(emptyList())
    }
    val listSearch: StateFlow<List<Feed>>
        get() = _listSearch.asStateFlow()
    private var searchJob: Deferred<TSDataState<SearchResponse>>? = null
    fun performSearch(searchText: String?) {
        searchJob?.cancel()
        searchText ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Keep the Deferred in a local: two rapid keystrokes could otherwise
            // interleave so that `searchJob!!` read a job another coroutine had
            // just replaced.
            val mode = _searchMode.value
            val job = async {
                when (mode) {
                    SearchMode.All -> _searchPodcasts(query = searchText)
                    SearchMode.Music -> _searchMusicFeeds(query = searchText)
                }
            }
            searchJob = job
            currentSearchText.value = searchText
            val rs = job.await()
            if (rs.isSuccess()) {
                val data = (rs as TSDataState.Success).data
                _listSearch.value = data.feeds
            } else {
                _listSearch.value = emptyList()
            }
        }
    }
}