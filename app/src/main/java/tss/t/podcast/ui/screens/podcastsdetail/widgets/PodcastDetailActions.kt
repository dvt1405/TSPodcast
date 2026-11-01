package tss.t.podcast.ui.screens.podcastsdetail.widgets

import tss.t.sharedlibrary.ui.glass.LocalLiquidGlass
import tss.t.sharedlibrary.ui.glass.glassSurface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tss.t.coreapi.models.Podcast
import tss.t.podcast.R
import tss.t.podcast.ui.screens.common.LocalShowActions
import tss.t.podcast.ui.screens.common.ShowRef
import tss.t.podcast.ui.screens.common.WebLinks
import tss.t.podcast.ui.screens.common.shareText

/**
 * Top-right buttons of Podcast detail (parity with iOS M2/M6): favourite the show, share a web
 * link, and a menu to hide the show (with confirmation, then leave) or report it.
 */
@Composable
fun PodcastDetailActions(
    podcast: Podcast,
    isFavourite: Boolean,
    onFavouriteChange: (Boolean) -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown on the white area beside the cover (tinted buttons) instead of over artwork. */
    onLight: Boolean = false,
) {
    val context = LocalContext.current
    val actions = LocalShowActions.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    val show = ShowRef(podcast.id, podcast.title.orEmpty(), podcast.image)
    val shareTitle = stringResource(R.string.share_chooser_title)
    val shareText = stringResource(R.string.share_podcast_text, podcast.title.orEmpty(), WebLinks.podcast(podcast.id, podcast.title))
    val hiddenMessage = stringResource(R.string.show_hidden_toast)

    Row(
        modifier = modifier
            .zIndex(2f)
            .then(if (onLight) Modifier else Modifier.statusBarsPadding())
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundButton(
            icon = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            description = stringResource(if (isFavourite) R.string.podcast_unfavourite else R.string.podcast_favourite),
            onLight = onLight,
            onClick = { onFavouriteChange(!isFavourite) }
        )
        RoundButton(
            icon = Icons.Rounded.Share,
            description = stringResource(R.string.share),
            onLight = onLight,
            onClick = {
                tss.t.podcast.analytics.ContentAnalytics.share(tss.t.sharedlibrary.analytics.AppEvents.ContentType.PODCAST, podcast.id)
                context.shareText(shareText, shareTitle)
            }
        )
        if (actions != null) {
            Box {
                RoundButton(
                    icon = Icons.Rounded.MoreVert,
                    description = stringResource(R.string.more_actions),
                    onLight = onLight,
                    onClick = { menuOpen = true }
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_hide)) },
                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                        onClick = {
                            menuOpen = false
                            confirmHide = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_report)) },
                        leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                        onClick = {
                            menuOpen = false
                            actions.report(show)
                        }
                    )
                }
            }
        }
    }

    if (confirmHide && actions != null) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text(stringResource(R.string.show_hide_confirm_title)) },
            text = { Text(stringResource(R.string.show_hide_confirm_message, podcast.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmHide = false
                    actions.hide(show)
                    Toast.makeText(context, hiddenMessage, Toast.LENGTH_SHORT).show()
                    onHidden()
                }) { Text(stringResource(R.string.show_hide)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmHide = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onLight: Boolean, onClick: () -> Unit) {
    val glass = LocalLiquidGlass.current
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                when {
                    onLight -> Modifier.clip(CircleShape).background(primary.copy(alpha = 0.10f))
                    glass -> Modifier.glassSurface(CircleShape, fill = Color.White.copy(alpha = 0.22f), elevation = 6.dp)
                    else -> Modifier.clip(CircleShape).background(Color.Black.copy(0.3f))
                }
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(10.dp)
    ) {
        Icon(icon, contentDescription = description, tint = if (onLight) primary else Color.White)
    }
}
