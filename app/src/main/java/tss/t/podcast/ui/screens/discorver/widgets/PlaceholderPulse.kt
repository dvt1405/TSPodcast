package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import tss.t.podcast.ui.theme.rememberAnimationsEnabled
import tss.t.sharedlibrary.theme.Colors

private val PulseFrom = Colors.Primary10.copy(alpha = 0.3f)
private val PulseTo = Colors.Secondary.copy(alpha = 0.3f)
private const val PULSE_DELAY_MS = 200L
private const val PULSE_MS = 1700L
private const val PULSE_CYCLE_MS = 2 * (PULSE_DELAY_MS + PULSE_MS)

/**
 * The Discover loading pulse (same colours and timing as the old infinite transition).
 *
 * The old one was read in composition by `by` at the top of Discover: the whole screen
 * recomposed every frame, forever, even with everything loaded. Here the colour is read in
 * draw only, and the clock only ticks while a placeholder is composed (on screen), so a
 * loaded Discover asks for no frames at all. All placeholders share one clock, so cards that
 * scroll in later pulse in step with the others.
 */
@Stable
class PlaceholderPulse internal constructor(internal val animate: Boolean) {
    internal val clockMs = mutableLongStateOf(0L)

    /** The current colour; call it from a draw block. */
    fun color(): Color = if (animate) colorAt(clockMs.longValue) else PulseFrom
}

@Composable
fun rememberPlaceholderPulse(): PlaceholderPulse {
    val animate = rememberAnimationsEnabled()
    return remember(animate) { PlaceholderPulse(animate) }
}

/** Keeps [pulse] ticking while the calling placeholder is in the composition. */
@Composable
fun PlaceholderPulseTicker(pulse: PlaceholderPulse) {
    if (!pulse.animate) return
    LaunchedEffect(pulse) {
        while (true) {
            withFrameMillis { pulse.clockMs.longValue = it }
        }
    }
}

/** Fills the (clipped) bounds with the pulse colour, read in draw: no recomposition per frame. */
fun Modifier.placeholderFill(shape: Shape, pulse: PlaceholderPulse): Modifier =
    clip(shape).drawBehind { drawRect(pulse.color()) }

// Same curve as infiniteRepeatable(tween(1700, delayMillis = 200), RepeatMode.Reverse).
private fun colorAt(timeMs: Long): Color {
    val t = timeMs % PULSE_CYCLE_MS
    val half = PULSE_DELAY_MS + PULSE_MS
    val forward = t < half
    val local = if (forward) t else t - half
    val progress = ((local - PULSE_DELAY_MS).coerceAtLeast(0L)).toFloat() / PULSE_MS
    val eased = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    return lerp(PulseFrom, PulseTo, if (forward) eased else 1f - eased)
}
