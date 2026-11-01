package tss.t.podcast.ui.screens.discorver.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tss.t.core.storage.PodcastLocation
import tss.t.coreapi.models.Podcast
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/**
 * "Podcast theo vị trí" on Home: one chip per podcast location (priority order), and for the
 * chosen one a featured card (#1) above a three-row ranked chart, so it reads differently from
 * the plain card rows around it. "Sắp xếp" opens the location editor.
 */
@Composable
fun LocationSection(
    locations: List<PodcastLocation>,
    podcastsByRegion: Map<String, List<Podcast>>,
    placeHolderColor: Color,
    onManage: () -> Unit,
    onClick: (Podcast) -> Unit,
) {
    if (locations.isEmpty()) return
    val displayLocale = LocalConfiguration.current.locales[0]
    // Back to the main location whenever the list is edited or reordered.
    var selectedRegion by rememberSaveable(locations.joinToString(",") { it.region }) {
        androidx.compose.runtime.mutableStateOf(locations.first().region)
    }
    val selected = locations.firstOrNull { it.region == selectedRegion } ?: locations.first()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.location_section_title), style = TextStyles.Title4)
                Text(stringResource(R.string.location_section_subtitle), style = TextStyles.Body5, color = Colors.Gray60)
            }
            TextButton(onClick = onManage) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.location_manage))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(locations, key = { it.region }) { location ->
                val isSelected = location.region == selected.region
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable { selectedRegion = location.region }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(location.flag, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        location.displayName(displayLocale),
                        style = TextStyles.SubTitle3,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        AnimatedContent(
            targetState = selected.region,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "LocationContent"
        ) { region ->
            val items = podcastsByRegion[region]
            when {
                items == null -> LocationPlaceholder(placeHolderColor)
                items.isEmpty() -> Text(
                    stringResource(R.string.location_empty),
                    style = TextStyles.SubTitle4,
                    color = Colors.Gray60,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                )
                else -> Column {
                    FeaturedCard(items.first(), onClick)
                    RankedGrid(items.drop(1).take(15), onClick)
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(podcast: Podcast, onClick: (Podcast) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), Colors.Secondary.copy(alpha = 0.10f))))
            .border(1.dp, primary.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable { onClick(podcast) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = podcast.image.ifEmpty { podcast.feedImage.orEmpty() },
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "★ " + stringResource(R.string.location_top_pick),
                style = TextStyles.SubTitle4,
                color = primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(podcast.title.orEmpty(), style = TextStyles.SubTitle1, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                podcast.description.orEmpty().replace(Regex("<[^>]*>"), "").trim(),
                style = TextStyles.Body5,
                color = Colors.Gray60,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val ROW_HEIGHT = 64.dp

@Composable
private fun RankedGrid(items: List<Podcast>, onClick: (Podcast) -> Unit) {
    if (items.isEmpty()) return
    val rows = minOf(3, items.size)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * rows + 8.dp * (rows - 1) + 24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, podcast ->
            Row(
                modifier = Modifier
                    .width(280.dp)
                    .height(ROW_HEIGHT)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick(podcast) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = podcast.image.ifEmpty { podcast.feedImage.orEmpty() },
                    contentDescription = podcast.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(ROW_HEIGHT).clip(RoundedCornerShape(12.dp))
                )
                Text(
                    "${index + 2}",
                    style = TextStyles.Title4,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp).padding(start = 10.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(podcast.title.orEmpty(), style = TextStyles.SubTitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        podcast.description.orEmpty().replace(Regex("<[^>]*>"), "").trim(),
                        style = TextStyles.SubTitle4,
                        color = Colors.Gray60,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPlaceholder(color: Color) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(20.dp)).background(color))
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(ROW_HEIGHT).clip(RoundedCornerShape(12.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.height(16.dp).width(180.dp).clip(RoundedCornerShape(6.dp)).background(color))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
