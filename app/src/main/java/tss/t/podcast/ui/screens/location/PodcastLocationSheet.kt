package tss.t.podcast.ui.screens.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.core.storage.PodcastLocation
import tss.t.core.storage.PodcastLocationPrefs
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/**
 * Edits the podcast locations: up to [PodcastLocationPrefs.MAX_LOCATIONS], reordered with the
 * arrows (the first one is the main location), removed (never the last one) or added from the
 * supported list. Every change is saved at once through [onChange] (regions in priority order).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastLocationSheet(
    locations: List<PodcastLocation>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val regions = locations.map { it.region }
    val full = locations.size >= PodcastLocationPrefs.MAX_LOCATIONS
    val others = PodcastLocationPrefs.SUPPORTED.filter { it.region !in regions }

    fun move(from: Int, to: Int) {
        if (to !in regions.indices) return
        onChange(regions.toMutableList().apply { add(to, removeAt(from)) })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        tss.t.podcast.analytics.TrackScreen(tss.t.sharedlibrary.analytics.AppEvents.Screen.LOCATION_SHEET)
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.location_sheet_title), style = TextStyles.Title4, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.location_done)) }
                }
                Text(
                    stringResource(R.string.location_sheet_hint),
                    style = TextStyles.SubTitle4,
                    color = Colors.Gray60,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                )
            }
            itemsIndexed(locations, key = { _, it -> "sel_" + it.region }) { index, location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (index == 0) 0.12f else 0.05f))
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = TextStyles.SubTitle3)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(location.flag, fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(location.displayName(displayLocale), style = TextStyles.SubTitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (index == 0) "${location.languageName()} · ${stringResource(R.string.location_primary)}" else location.languageName(),
                            style = TextStyles.SubTitle4,
                            color = Colors.Gray60,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.location_move_up))
                    }
                    IconButton(onClick = { move(index, index + 1) }, enabled = index < locations.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.location_move_down))
                    }
                    IconButton(onClick = { onChange(regions - location.region) }, enabled = locations.size > 1) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.location_remove))
                    }
                }
            }
            item {
                Text(
                    if (full) stringResource(R.string.location_limit_reached) else stringResource(R.string.location_add_title),
                    style = TextStyles.SubTitle3,
                    color = if (full) Colors.Gray60 else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 6.dp)
                )
            }
            items(others, key = { "add_" + it.region }) { location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (full) 0.4f else 1f)
                        .clickable(enabled = !full, role = Role.Button) { onChange(regions + location.region) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(location.flag, fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text(location.displayName(displayLocale), style = TextStyles.SubTitle2)
                        Text(location.languageName(), style = TextStyles.SubTitle4, color = Colors.Gray60)
                    }
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}
