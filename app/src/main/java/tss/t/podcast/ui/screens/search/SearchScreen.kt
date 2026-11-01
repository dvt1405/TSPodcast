package tss.t.podcast.ui.screens.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.json.JSONObject
import tss.t.ads.AdBannerSlot
import tss.t.ads.AdPlacement
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.Feed
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.R
import tss.t.podcast.ui.screens.search.widgets.SearchModeFilter
import tss.t.podcast.ui.screens.search.widgets.SearchPodcastItem
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSOutlinedTextField
import tss.t.podcast.ui.screens.search.widgets.inCategory

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    initSearchText: String? = null,
    trendingKeyword: String = stringResource(R.string.search_suggest_key_title),
    hazeState: HazeState,
    onSearch: (String?) -> Unit = {},
    categories: List<CategoryRes.Category> = emptyList(),
    searchResult: List<Feed> = emptyList(),
    searchMode: SearchMode = SearchMode.All,
    onSearchModeSelected: (SearchMode) -> Unit = {},
    onSearchSelected: (Feed) -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(),
    selectedCategory: CategoryRes.Category? = null,
    categoryResults: List<Feed>? = null,
    primaryLocation: tss.t.core.storage.PodcastLocation? = null,
    onCategorySelected: (CategoryRes.Category?) -> Unit = {},
) {
    var showAllCategories by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showAllCategories) {
        tss.t.podcast.ui.screens.search.widgets.AllCategoriesSheet(
            categories = categories,
            onSelect = {
                showAllCategories = false
                onCategorySelected(it)
            },
            onDismiss = { showAllCategories = false }
        )
    }
    // Topic filter over text results; reset when the results change.
    var resultCategory by remember(searchResult) { mutableStateOf<Int?>(null) }
    var searchText by remember {
        mutableStateOf(initSearchText)
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(Unit) {
        if (searchText.isNullOrEmpty() && searchResult.isEmpty()) {
            focusRequester.requestFocus()
        }
        snapshotFlow { searchText }
            .debounce(200L)
            .collectLatest {
                onSearch(searchText)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    focusManager.clearFocus(true)
                },
                indication = null
            )
    ) {
        Spacer(modifier = Modifier.size(innerPadding.calculateTopPadding()))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TSOutlinedTextField(
                searchText ?: "",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                onValueChange = {
                    searchText = it
                },
                colors = TextFieldDefaults.colors(
                    disabledIndicatorColor = Colors.Primary10,
                    focusedIndicatorColor = Colors.Primary.copy(0.5f),
                    unfocusedIndicatorColor = Colors.Primary10,
                    unfocusedContainerColor = Colors.White,
                    focusedContainerColor = Colors.Secondary.copy(0.1f),
                    unfocusedLabelColor = Colors.Gray50,
                    focusedLabelColor = Colors.Gray50,
                    disabledLabelColor = Colors.Gray50,
                    cursorColor = Colors.Primary,
                    errorIndicatorColor = Colors.Red50,
                    errorCursorColor = Colors.Red50,
                ),
                imeAction = ImeAction.Done,
                shape = RoundedCornerShape(1000.dp),
                label = null,
                placeholder = stringResource(R.string.search_hint_title),
                trailingIcon = if (searchText.isNullOrEmpty()) null else @Composable {
                    {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.show_search_clear),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    searchText = ""
                                }
                                .padding(8.dp)
                        )
                    }
                },
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                )
            )

        }
        SearchModeFilter(
            selected = searchMode,
            onModeSelected = onSearchModeSelected,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        )
        AdBannerSlot(placement = AdPlacement.SearchResults)
        LazyColumn(
            modifier = Modifier
                .haze(
                    state = hazeState,
                    style = HazeDefaults.tint
                )
                // No animateContentSize: it re-measured the whole list on every result change.
                .fillMaxWidth()
        ) {
            if (searchText.isNullOrEmpty() && selectedCategory != null) {
                // A topic is open: its trending shows in the main podcast location.
                item(key = "categoryHeader") {
                    if (primaryLocation != null) {
                        tss.t.podcast.ui.screens.search.widgets.SelectedCategoryHeader(
                            category = selectedCategory,
                            location = primaryLocation,
                            onClear = { onCategorySelected(null) }
                        )
                    }
                }
                when {
                    categoryResults == null -> item(key = "categoryLoading") {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    categoryResults.isEmpty() -> item(key = "categoryEmpty") {
                        Text(
                            stringResource(R.string.search_category_empty),
                            style = TextStyles.Body4,
                            color = Colors.Gray60,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp)
                        )
                    }
                    else -> items(
                        categoryResults.size,
                        key = { "cat_" + categoryResults[it].id },
                        contentType = { "result" }
                    ) { index ->
                        SearchPodcastItem(categoryResults[index]) { onSearchSelected(it) }
                    }
                }
            } else if (searchText.isNullOrEmpty()) {
                if (categories.isNotEmpty() && searchMode == SearchMode.All) {
                    item(key = "categoryStrip") {
                        tss.t.podcast.ui.screens.search.widgets.CategoryStrip(
                            categories = categories,
                            onSelect = {
                                focusManager.clearFocus()
                                onCategorySelected(it)
                            },
                            onSeeAll = {
                                focusManager.clearFocus()
                                showAllCategories = true
                            }
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.try_search_with_keyword_title, trendingKeyword),
                        style = TextStyles.Title5,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp)
                            .clickable { searchText = trendingKeyword }
                    )
                }
            } else if (searchResult.isEmpty() && !searchText.isNullOrEmpty()) {
                item {
                    EmptySearchWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 70.dp)
                            .padding(horizontal = 40.dp),
                        searchText = searchText
                    )
                    if (searchMode == SearchMode.Music) {
                        // The medium=music catalogue is small and skews English,
                        // so an empty result here is expected rather than a bug.
                        Text(
                            stringResource(R.string.search_music_empty_hint),
                            style = TextStyles.Body4,
                            color = Colors.Gray60,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp)
                                .padding(top = 12.dp)
                        )
                    }
                }
            } else {
                val shown = resultCategory?.let { id ->
                    searchResult.filter { feed -> feed.inCategory(id) }
                } ?: searchResult
                items(
                    shown.size,
                    contentType = { if (it == 0) "resultHeader" else "result" }
                ) { index ->
                    val item = shown[index]
                    if (index == 0) {
                        Text(
                            stringResource(
                                if (searchMode == SearchMode.Music) {
                                    R.string.search_music_result_title
                                } else {
                                    R.string.search_result_title
                                },
                                (searchText ?: "")
                            ),
                            style = TextStyles.Title5,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp)
                        )
                        tss.t.podcast.ui.screens.search.widgets.ResultCategoryFilter(
                            results = searchResult,
                            selectedId = resultCategory,
                            onSelect = { resultCategory = it }
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    SearchPodcastItem(item) {
                        onSearchSelected(it)
                    }
                }
            }
            item {
                Spacer(Modifier.size(innerPadding.calculateBottomPadding() + 16.dp))
            }
        }
    }
}

@Composable
fun EmptySearchWidget(
    modifier: Modifier = Modifier,
    searchText: String?
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(tss.t.sharedresources.R.drawable.ic_empty_search),
            contentDescription = null,
            colorFilter = null
        )
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.empty_search_title))
                withStyle(TextStyles.Title5.toSpanStyle()) {
                    append(searchText)
                }
            },
            style = TextStyles.Body3
        )
    }
}

@Composable
@Preview
fun SearchScreenPreview() {
    val categories = LocalContext.current.assets.open("categories.json")
        .bufferedReader()
        .readText()
    val listFeed = mutableListOf<CategoryRes.Category>()
    val jsArr = JSONObject(categories).getJSONArray("Categories")
    for (i in 0 until jsArr.length()) {
        val js = jsArr.getJSONObject(i)
        listFeed.add(
            CategoryRes.Category(
                js.optInt("id"),
                js.optString("name")
            )
        )
    }
    PodcastTheme {
        SearchScreen(
            categories = listFeed,
            hazeState = HazeState()
        )
    }
}