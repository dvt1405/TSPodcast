package tss.t.sharedlibrary.utils


import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors

internal val dividerBrush = Brush.linearGradient(
    listOf(
        Colors.Primary10,
        Colors.Secondary,
        Colors.Primary10
    )
)

fun Modifier.drawDivider(
    brush: Brush = dividerBrush,
    horizontalPaddingDp: Dp = 16.dp,
    dividerSizeDp: Dp = 1.dp
) = composed {
    this.drawWithContent {
        val contentPadding = horizontalPaddingDp.toPx()
        val dividerSize = dividerSizeDp.toPx()
        drawContent()
        drawLine(
            brush,
            strokeWidth = dividerSize,
            start = Offset(
                x = 0f,
                y = size.height - dividerSize + contentPadding
            ),
            end = Offset(
                x = size.width,
                y = size.height - dividerSize + contentPadding
            )
        )
    }
}