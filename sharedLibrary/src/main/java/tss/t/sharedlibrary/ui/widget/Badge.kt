package tss.t.sharedlibrary.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

@Composable
fun TSBadge(
    badgeTitle: String,
    icon: @Composable () -> Unit = {},
    color: Color = Colors.Primary,
    contentColor: Color = Colors.White,
    textStyle: TextStyle = TextStyles.Body5,
    modifier: Modifier = Modifier
) {
    Badge(
        containerColor = color,
        contentColor = contentColor,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = badgeTitle,
                style = textStyle,
                color = contentColor,
                modifier = modifier.padding(1.dp)
            )
        }

    }
}

@Composable
fun TSBadge(
    badgeTitle: String,
    icon: ImageVector? = null,
    color: Color = Colors.Primary,
    contentColor: Color = Colors.White,
    iconSize: Dp = 8.dp,
    textStyle: TextStyle = TextStyles.Body5,
    modifier: Modifier = Modifier,
    iconClick: () -> Unit = {}
) {
    TSBadge(
        badgeTitle = badgeTitle,
        icon = {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = iconSize / 2)
                        .size(iconSize)
                        .clickable {
                            iconClick()
                        }
                )
            }
        },
        color = color,
        contentColor = contentColor,
        textStyle = textStyle,
        modifier = modifier
    )
}