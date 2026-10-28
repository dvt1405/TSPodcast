package tss.t.podcast.ui.screens.search.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.podcast.ui.screens.search.SearchMode
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/**
 * Two-item filter deciding which search endpoint the query goes to.
 *
 * Built here rather than with sharedLibrary's [tss.t.sharedlibrary.ui.widget.Tabs]
 * because that one drives a sliding indicator through offset/size callbacks,
 * which is more machinery than a two-item toggle needs.
 */
@Composable
fun SearchModeFilter(
    selected: SearchMode,
    modifier: Modifier = Modifier,
    onModeSelected: (SearchMode) -> Unit = {}
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchMode.entries.forEach { mode ->
            SearchModeChip(
                text = stringResource(mode.labelRes),
                isSelected = mode == selected,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun SearchModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) Colors.Primary10 else Colors.White,
        label = "SearchModeChipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Colors.Primary else Colors.Gray60,
        label = "SearchModeChipContent"
    )
    Text(
        text = text,
        style = if (isSelected) TextStyles.SubTitle3 else TextStyles.Body4,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .border(
                width = if (isSelected) 1.dp else Dp.Hairline,
                color = if (isSelected) Colors.Primary.copy(alpha = .5f) else Colors.Primary10,
                shape = RoundedCornerShape(1000.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchModeFilterPreview() {
    SearchModeFilter(
        selected = SearchMode.Music,
        modifier = Modifier.padding(16.dp)
    )
}
