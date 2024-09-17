package tss.t.sharedlibrary.ui.animations.skeleton

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter.Companion.DefaultTransform
import coil.compose.AsyncImagePainter.State

fun Modifier.shimmer(): Modifier = composed {
    val shimmerColors = listOf(
        Color(0XFFFBFBFC).copy(alpha = 0.6f),
        Color(0XFFE6E9EC).copy(alpha = 0.6f),
        Color.Transparent,
    )
    val transition = rememberInfiniteTransition(label = "infiniteTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "floatAnimation"
    )
    drawBehind {
        drawRect(
            Color(0XFFE6E9EC).copy(alpha = 0.6f),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(x = 0f, y = translateAnimation.value),
                end = Offset(x = translateAnimation.value, y = translateAnimation.value)
            )
        )
    }
}

@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    transform: (State) -> State = DefaultTransform,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    @DrawableRes placeholder: Int? = null,
) {
    val imageLoadedSuccess = remember {
        mutableStateOf(false)
    }
    val imageLoadedFailed = remember {
        mutableStateOf(false)
    }
    AsyncImage(
        model = if (imageLoadedFailed.value) placeholder ?: model else model,
        contentDescription = contentDescription,
        modifier = if (imageLoadedSuccess.value) modifier else modifier.shimmer(),
        transform = transform,
        onState = { state ->
            if (state is State.Success) {
                imageLoadedSuccess.value = true
            }
            if (state is State.Error) {
                imageLoadedFailed.value = true
            }
        },
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
    )
}
