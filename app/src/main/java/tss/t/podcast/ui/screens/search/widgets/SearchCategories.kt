package tss.t.podcast.ui.screens.search.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tss.t.core.storage.PodcastLocation
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.Feed
import tss.t.featureonboarding.CategoryNames
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/** Popular topics first (what Vietnamese listeners pick most), then the rest by id. */
fun orderedCategories(categories: List<CategoryRes.Category>): List<CategoryRes.Category> {
    val rank = CategoryNames.POPULAR_IDS.withIndex().associate { (i, id) -> id to i }
    return categories.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.id }))
}

/**
 * Topics as a two-row strip that scrolls sideways (popular first), instead of a chip wall
 * filling the screen; "Xem tất cả" opens every topic in a sheet.
 */
@Composable
fun CategoryStrip(
    categories: List<CategoryRes.Category>,
    onSelect: (CategoryRes.Category) -> Unit,
    onSeeAll: () -> Unit,
) {
    val ordered = remember(categories) { orderedCategories(categories).take(24) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.search_categories_title), style = TextStyles.Title5, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.search_categories_see_all)) }
        }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(CHIP_HEIGHT * 2 + 8.dp + 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ordered, key = { it.id }) { category ->
                CategoryChip(category, selected = false, onClick = { onSelect(category) })
            }
        }
    }
}

private val CHIP_HEIGHT = 44.dp

@Composable
private fun CategoryChip(
    category: CategoryRes.Category,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .widthIn(min = 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) primary else primary.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryNames.iconRes(category.id)?.let { icon ->
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Colors.White),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            CategoryNames.name(category.id, category.name),
            style = TextStyles.SubTitle3,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Every topic, three columns, in a sheet; only this list scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesSheet(
    categories: List<CategoryRes.Category>,
    onSelect: (CategoryRes.Category) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = remember(categories) { orderedCategories(categories) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.search_categories_sheet_title),
            style = TextStyles.Title4,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ordered, key = { it.id }) { category ->
                CategoryChip(category, selected = false, modifier = Modifier.fillMaxWidth(), onClick = { onSelect(category) })
            }
        }
    }
}

/** The opened topic: a filled chip to close it, and where its shows come from. */
@Composable
fun SelectedCategoryHeader(
    category: CategoryRes.Category,
    location: PodcastLocation,
    onClear: () -> Unit,
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Row(
            modifier = Modifier
                .height(CHIP_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClear)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                CategoryNames.name(category.id, category.name),
                style = TextStyles.SubTitle2,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Close, stringResource(R.string.show_search_clear), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        Text(
            stringResource(R.string.search_category_trending_in, "${location.flag} ${location.displayName(displayLocale)}"),
            style = TextStyles.SubTitle4,
            color = Colors.Gray60,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
    }
}

/**
 * Filters search results by topic: "Tất cả" plus the topics most common among the results
 * (at most 8), so the row stays short and every chip has results.
 */
@Composable
fun ResultCategoryFilter(
    results: List<Feed>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
) {
    val top = remember(results) { topCategories(results) }
    if (top.size < 2) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterPill(stringResource(R.string.search_filter_all), selectedId == null) { onSelect(null) }
        }
        items(top, key = { it.first }) { (id, name) ->
            FilterPill(CategoryNames.name(id, name), selectedId == id) { onSelect(id) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        label,
        style = TextStyles.SubTitle3,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary else primary.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** (id, English name) of the topics most common among [results]. */
fun topCategories(results: List<Feed>, max: Int = 8): List<Pair<Int, String>> =
    results.flatMap { feed -> feed.categories.orEmpty().entries }
        .mapNotNull { (id, name) -> id.toIntOrNull()?.let { it to name } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(max)
        .map { it.key }

/** Whether [feed] is in topic [categoryId]. */
fun Feed.inCategory(categoryId: Int): Boolean =
    categories.orEmpty().keys.any { it.toIntOrNull() == categoryId }
