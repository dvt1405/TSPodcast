package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import tss.t.podcast.ui.theme.rememberAnimationsEnabled
import tss.t.sharedlibrary.theme.Colors

/** Placeholder colour for loading cards: a provider, so it is read in the draw phase only. */
typealias PlaceholderColor = () -> Color

private val PulseFrom = Colors.Primary10.copy(alpha = 0.3f)
private val PulseTo = Colors.Secondary.copy(alpha = 0.3f)

/**
 * The Discover loading pulse. Runs only while [active] (some section still shows
 * placeholders) and animations are on; otherwise a static colour, so a loaded screen stops
 * asking for frames.
 */
@Composable
fun rememberPlaceholderPulse(active: Boolean): PlaceholderColor {
    if (!active || !rememberAnimationsEnabled()) {
        return remember { { PulseFrom } }
    }
    val transition = rememberInfiniteTransition(label = "PlaceholderPulse")
    val color = transition.animateColor(
        initialValue = PulseFrom,
        targetValue = PulseTo,
        animationSpec = infiniteRepeatable(
            tween(1700, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Color"
    )
    return remember(color) { { color.value } }
}

/** Fills the (clipped) bounds with [color], reading it in draw: no recomposition per frame. */
fun Modifier.placeholderFill(shape: Shape, color: PlaceholderColor): Modifier =
    clip(shape).drawBehind { drawRect(color()) }
