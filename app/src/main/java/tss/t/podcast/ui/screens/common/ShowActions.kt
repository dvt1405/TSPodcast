package tss.t.podcast.ui.screens.common

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tss.t.podcast.R

/** A show as the long-press menu needs it. */
data class ShowRef(val feedId: Long, val title: String, val image: String?)

/**
 * Hide a show / report it (parity with iOS M6 N1 lite and content report). Provided once at the
 * root; every show card opens [ShowActionsMenu] on long press.
 */
class ShowActions(
    val hide: (ShowRef) -> Unit,
    val report: (ShowRef) -> Unit,
)

val LocalShowActions = staticCompositionLocalOf<ShowActions?> { null }

@Composable
fun ShowActionsMenu(
    show: ShowRef,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val actions = LocalShowActions.current ?: return
    val context = LocalContext.current
    val hiddenMessage = stringResource(R.string.show_hidden_toast)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.show_hide)) },
            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
            onClick = {
                onDismiss()
                actions.hide(show)
                Toast.makeText(context, hiddenMessage, Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.show_report)) },
            leadingIcon = { Icon(Icons.Rounded.Flag, null) },
            onClick = {
                onDismiss()
                actions.report(show)
            }
        )
    }
}
