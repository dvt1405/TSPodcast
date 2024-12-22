package tss.t.podcast.ui.screens.player.widgets

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import java.util.Formatter
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    start: Long = 0L,
    end: Long = 10_000L,
    onProgressChanged: (Float) -> Unit = {}
) {
    val formatBuilder = remember {
        StringBuilder()
    }
    val formatter = remember {
        Formatter(formatBuilder, Locale.getDefault())
    }
    var isDragging by remember {
        mutableStateOf(false)
    }
    val transition = updateTransition(isDragging, label = "")
    val animateScaleY by transition.animateFloat(label = "ScaleY") {
        if (it) {
            1.2f
        } else {
            1f
        }
    }

    val animateProgress = remember {
        Animatable(progress)
    }
    LaunchedEffect(progress) {
        if (animateProgress.targetValue != progress && !isDragging) {
            animateProgress.animateTo(progress)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier
            .defaultMinSize(minHeight = 6.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { p, dragAmount ->
                        if (isDragging) {
                            coroutineScope.launch {
                                val targetValue = dragAmount / size.width + animateProgress.value
                                animateProgress.snapTo(targetValue)
                            }
                        }
                    },
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        onProgressChanged(animateProgress.value)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
    ) {
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(
                    Colors.Primary.copy(0.1f),
                    Colors.Secondary.copy(0.1f)
                )
            ),
            size = Size(size.width, size.height * 0.5f),
            topLeft = Offset(0f, size.height * 0.25f),
            cornerRadius = CornerRadius(100.dp.toPx())
        )

        drawRoundRect(
            Brush.horizontalGradient(
                listOf(
                    Colors.Primary,
                    Colors.Secondary
                )
            ),
            size = Size(
                (size.width * animateProgress.value)
                    .coerceIn(0f, size.width),
                size.height * 0.5f * animateScaleY
            ),
            topLeft = Offset(0f, size.height * 0.25f * animateScaleY),
            cornerRadius = CornerRadius(100.dp.toPx())
        )

        drawCircle(
            Colors.Primary,
            radius = 4.dp.toPx() * animateScaleY,
            center = Offset(
                x = ((size.width - 4.dp.toPx()) * animateProgress.value)
                    .coerceIn(0f, size.width),
                y = size.height / 2 * animateScaleY
            )
        )
        if (isDragging) {
            val textLayout = textMeasurer.measure(
                Util.getStringForTime(
                    formatBuilder,
                    formatter,
                    ((end - start) * animateProgress.value).toLong()
                ),
                style = TextStyles.SubTitle4,
                maxLines = 1,
            )

            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    x = (size.width - 4.dp.toPx()) * animateProgress.value - textLayout.size.width / 2,
                    y = -textLayout.size.height.toFloat()
                )
            )
        }
    }
}

@Composable
@Preview
fun PlayerProgressPreview() {
    Box(Modifier.fillMaxSize()) {
        PlayerProgress(
            0.3f,
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(8.dp)
        )
    }
}