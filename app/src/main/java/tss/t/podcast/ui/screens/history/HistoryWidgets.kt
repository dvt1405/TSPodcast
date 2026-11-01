package tss.t.podcast.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tss.t.core.history.ListeningHistoryRules
import tss.t.core.models.ListeningHistory
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.utils.imageRequestBuilder
import kotlin.math.ceil

/** Cover of a history entry, with the app's usual placeholder and fallback. */
@Composable
internal fun HistoryArtwork(entry: ListeningHistory, size: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = imageRequestBuilder(LocalContext.current)
            .data(entry.image)
            .placeholder(tss.t.sharedresources.R.drawable.image_loader_place_holder_12dp)
            .error(tss.t.sharedresources.R.drawable.onboarding_slide_7)
            .crossfade(200)
            .build(),
        contentDescription = null,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

/** "Đã nghe xong", or a progress bar with the time left ("Còn 12 phút"). */
@Composable
internal fun HistoryProgress(entry: ListeningHistory, modifier: Modifier = Modifier) {
    if (entry.finished) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Colors.Green50,
                modifier = Modifier.size(14.dp)
            )
            Text(
                stringResource(R.string.history_finished),
                style = TextStyles.Body5,
                color = Colors.Green50
            )
        }
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { ListeningHistoryRules.progress(entry) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp),
            color = Colors.Primary,
            trackColor = Colors.Primary10,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Text(
            text = remainingLabel(entry),
            style = TextStyles.Body5,
            color = Colors.Gray60
        )
    }
}

@Composable
private fun remainingLabel(entry: ListeningHistory): String {
    val remaining = ListeningHistoryRules.remainingMs(entry)
    if (remaining <= 0L) return formatClock(entry.positionMs)
    val minutes = ceil(remaining / 60_000.0).toInt().coerceAtLeast(1)
    return stringResource(R.string.history_minutes_left, minutes)
}

/** 12:05 or 1:02:05. */
internal fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
