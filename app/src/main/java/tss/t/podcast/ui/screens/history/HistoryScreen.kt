@file:OptIn(ExperimentalMaterial3Api::class)

package tss.t.podcast.ui.screens.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tss.t.core.history.HistoryDay
import tss.t.core.models.ListeningHistory
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.HazeStyle
import tss.t.hazeandroid.HazeTint
import tss.t.hazeandroid.haze
import tss.t.hazeandroid.hazeChild
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/** Height of the mini player floating over the list, so the last row is not hidden. */
private val MiniPlayerSpace = 86.dp

/**
 * "Lịch sử nghe": every episode played on this device, grouped by day, newest first. Tap plays
 * from where it was left; swipe left or the ⋮ menu removes one; the top bar clears everything
 * after a confirmation. Opened from the Favourites tab, Settings and the "Nghe tiếp" row.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onPlay: (ListeningHistory) -> Unit,
    hazeState: HazeState = remember { HazeState() },
) {
    val days by viewModel.days.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val isEmpty = days?.isEmpty() == true
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAll()
                }) {
                    Text(stringResource(R.string.history_clear_confirm), color = Colors.Red50)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState, style = HazeDefaults.tint)
        ) {
            item(key = "top") { Spacer(Modifier.height(topInset + 64.dp)) }
            if (isEmpty) {
                item(key = "empty") { EmptyHistory() }
            }
            days.orEmpty().forEach { day ->
                item(key = "day-${day.dayStartMillis}") { DayHeader(day) }
                items(day.entries, key = { it.mediaId }) { entry ->
                    SwipeToRemove(onRemove = { viewModel.remove(entry) }) {
                        HistoryRow(
                            entry = entry,
                            onClick = { onPlay(entry) },
                            onRemove = { viewModel.remove(entry) }
                        )
                    }
                }
            }
            item(key = "bottom") {
                Spacer(Modifier.height(bottomInset + 16.dp + if (hasMiniPlayer) MiniPlayerSpace else 0.dp))
            }
        }

        HistoryTopBar(
            hazeState = hazeState,
            canClear = !days.isNullOrEmpty(),
            onBack = onBack,
            onClear = { confirmClear = true }
        )
    }
}

@Composable
private fun HistoryTopBar(
    hazeState: HazeState,
    canClear: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.history_title), style = TextStyles.Title4) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
            }
        },
        actions = {
            if (canClear) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.history_clear))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        // Same frosted bar as the home tabs (MainTSAppBar): the list blurs under it.
        modifier = Modifier.hazeChild(
            hazeState,
            HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.surface,
                tints = listOf(
                    HazeTint.Brush(
                        Brush.verticalGradient(
                            listOf(
                                Colors.White.copy(alpha = 0.8f),
                                Colors.White.copy(alpha = 0.6f),
                                Colors.White.copy(alpha = 0.5f),
                            )
                        )
                    )
                ),
                blurRadius = 32.dp,
                noiseFactor = 0.15f,
            )
        )
    )
}

@Composable
private fun DayHeader(day: HistoryDay) {
    val context = LocalContext.current
    val label = when (day.kind) {
        HistoryDay.Kind.Today -> stringResource(R.string.history_today)
        HistoryDay.Kind.Yesterday -> stringResource(R.string.history_yesterday)
        HistoryDay.Kind.Earlier -> remember(day.dayStartMillis) {
            DateUtils.formatDateTime(
                context,
                day.dayStartMillis,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE
            )
        }
    }
    Text(
        text = label,
        style = TextStyles.Title5,
        color = Colors.TextTitleColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Colors.Red50)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.history_remove),
                    tint = Colors.White
                )
            }
        }
    ) {
        content()
    }
}

@Composable
private fun HistoryRow(
    entry: ListeningHistory,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryArtwork(entry, size = 60)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title,
                style = TextStyles.Title6,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            entry.podcastTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = TextStyles.SubTitle4,
                    color = Colors.TextDescriptionColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HistoryProgress(entry, Modifier.padding(top = 4.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.history_more), tint = Colors.Gray60)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_remove)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = Colors.Primary.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            stringResource(R.string.history_empty_title),
            style = TextStyles.Title5,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.history_empty_message),
            style = TextStyles.SubTitle4,
            color = Colors.Gray60,
            textAlign = TextAlign.Center
        )
    }
}
