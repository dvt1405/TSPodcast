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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.json.JSONObject
import tss.t.ads.MaxAdViewComposable
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.Feed
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.podcast.R
import tss.t.podcast.ui.screens.search.widgets.SearchPodcastItem
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSOutlinedTextField

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    initSearchText: String? = null,
    trendingKeyword: String = stringResource(R.string.search_suggest_key_title),
    hazeState: HazeState,
    onSearch: (String?) -> Unit = {},
    categories: List<CategoryRes.Category> = emptyList(),
    searchResult: List<Feed> = emptyList(),
    onSearchSelected: (Feed) -> Unit = {},
    innerPadding: PaddingValues = PaddingValues()
) {
    var searchText by remember {
        mutableStateOf(initSearchText)
    }
    var selectedCategory by remember {
        mutableStateOf<CategoryRes.Category?>(null)
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
                            contentDescription = "Close",
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
        MaxAdViewComposable(
            tsAnalytics = LocalAnalyticsScope.current!!
        )
        LazyColumn(
            modifier = Modifier
                .haze(
                    state = hazeState,
                    style = HazeDefaults.tint
                )
                .fillMaxWidth()
                .animateContentSize()
        ) {
            if (searchText.isNullOrEmpty()) {
                item {
                    Text(
                        stringResource(R.string.try_search_with_keyword_title, trendingKeyword),
                        style = TextStyles.Title5,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp)
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
                }
            } else {
                items(searchResult.size) { index ->
                    val item = searchResult[index]
                    if (index == 0) {
                        Text(
                            stringResource(R.string.search_result_title, (searchText ?: "")),
                            style = TextStyles.Title5,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp)
                        )
                        Spacer(Modifier.size(16.dp))
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
    val context = LocalContext.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(tss.t.sharedresources.R.drawable.ic_empty_search),
            contentDescription = "EmptySearch",
            colorFilter = null
        )
        Text(
            buildAnnotatedString {
                append(context.getString(R.string.empty_search_title))
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