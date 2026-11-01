package tss.t.podcast.ui.screens.discorver.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tss.t.core.models.ListeningHistory
import tss.t.podcast.R
import tss.t.podcast.ui.screens.history.HistoryArtwork
import tss.t.podcast.ui.screens.history.HistoryProgress
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/**
 * "Nghe tiếp" at the top of Discover: the episodes left part-way (app closed, process killed,
 * playback stopped by a call or another app), last played first. A tap continues from the saved
 * position; "Xem tất cả" opens the listening history. Hidden when there is nothing to continue.
 */
@Composable
fun ContinueListeningSection(
    entries: List<ListeningHistory>,
    onPlay: (ListeningHistory) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.continue_listening_title),
                style = TextStyles.Title4,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.continue_listening_see_all), style = TextStyles.SubTitle3, color = Colors.Primary)
            }
        }
        // One card fills the row; several peek so the row reads as scrollable.
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val cardWidth = if (entries.size == 1) screenWidth - 32.dp else (screenWidth * 0.82f)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.mediaId }) { entry ->
                ContinueCard(
                    entry = entry,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onPlay(entry) }
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(entry: ListeningHistory, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Colors.Secondary10)
            .border(1.dp, Colors.StrokeColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryArtwork(entry, size = 64)
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
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.continue_listening_play),
            tint = Colors.White,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Colors.Primary)
                .padding(8.dp)
        )
    }
}
