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
import kotlinx.coroutines.flow.stateIn
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
    private val _searchMusicFeeds: SearchMusicFeeds,
    private val contentPrefs: tss.t.core.storage.ContentPrefs,
    private val getPodcastsByLocation: tss.t.podcasts.usecase.GetPodcastsByLocation,
    private val locationPrefs: tss.t.core.storage.PodcastLocationPrefs,
) : ViewModel() {

    /** Topic opened from the category grid; its trending shows replace the suggestions. */
    private val _selectedCategory = MutableStateFlow<CategoryRes.Category?>(null)
    val selectedCategory: StateFlow<CategoryRes.Category?> = _selectedCategory.asStateFlow()

    /** Trending shows of [selectedCategory] in the main podcast location; null while loading. */
    private val _categoryResults = MutableStateFlow<List<Feed>?>(null)
    val categoryResults: StateFlow<List<Feed>?> = _categoryResults.asStateFlow()

    /** Main podcast location, shown next to category results. */
    val primaryLocation: tss.t.core.storage.PodcastLocation get() = locationPrefs.primary

    private var categoryJob: Job? = null

    fun selectCategory(category: CategoryRes.Category?) {
        categoryJob?.cancel()
        _selectedCategory.value = category
        _categoryResults.value = null
        category ?: return
        tss.t.sharedlibrary.analytics.Analytics.log(
            tss.t.sharedlibrary.analytics.AppEvents.categorySelect(category.id, category.name, "search")
        )
        categoryJob = viewModelScope.launch {
            val result = getPodcastsByLocation(
                locationPrefs.primary,
                cat = category.name,
                max = 60,
                sinceDays = 30,
            )
            val hidden = contentPrefs.hiddenShows.value.mapTo(HashSet()) { it.feedId }
            _categoryResults.value = (result as? TSDataState.Success)?.data.orEmpty()
                .filterNot { it.id in hidden }
                .map { it.toFeed() }
        }
    }

    /** A trending entry as a search row (only the fields the row and the detail screen use). */
    private fun tss.t.coreapi.models.Podcast.toFeed(): Feed = Feed(
        artwork = image.ifEmpty { feedImage.orEmpty() },
        author = "",
        categories = categories,
        contentType = "",
        crawlErrors = 0,
        dead = 0,
        description = description.orEmpty(),
        newestItemPubdate = datePublished,
        lastHttpStatus = 200,
        lastUpdateTime = dateCrawled,
        generator = "",
        ownerName = "",
        link = link.orEmpty(),
        type = 0,
        episodeCount = 0,
        imageUrlHash = 0L,
        lastCrawlTime = dateCrawled,
        lastParseTime = dateCrawled,
        itunesType = null,
        originalUrl = "",
        parseErrors = 0,
        podcastGuid = guid.orEmpty(),
        id = id,
        url = "",
        image = image.ifEmpty { feedImage.orEmpty() },
        title = title.orEmpty(),
        value = null,
        locked = 0,
        medium = "podcast",
        explicit = explicit == 1,
        itunesId = feedItunesId,
        language = feedLanguage.orEmpty(),
        lastGoodHttpStatusTime = 0,
        funding = null,
    )

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
    /** Results without the shows the user hides from here or anywhere else (iOS M6 N1 lite). */
    val listSearch: StateFlow<List<Feed>> by lazy {
        kotlinx.coroutines.flow.combine(_listSearch, contentPrefs.hiddenShows) { feeds, hidden ->
            val ids = hidden.mapTo(HashSet()) { it.feedId }
            feeds.filterNot { it.id in ids }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    }
    private var searchJob: Deferred<TSDataState<SearchResponse>>? = null
    private val searchLogger = tss.t.podcast.analytics.SettledSearchLogger(viewModelScope)
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
                searchLogger.onResult(searchText, mode.name.lowercase(), data.feeds.size)
            } else {
                _listSearch.value = emptyList()
            }
        }
    }
}