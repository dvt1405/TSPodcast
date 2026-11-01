package tss.t.podcast.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry

/**
 * One set of durations and curves for the whole app (Material 3 motion tokens), so screens,
 * tabs, sheets and the mini player move the same way. Compose scales every duration below by
 * the system animator duration scale, and skips them when "Remove animations" is on.
 */
object Motion {
    /** Material 3 "emphasized decelerate": elements entering the screen. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 "emphasized accelerate": elements leaving the screen. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val Standard = FastOutSlowInEasing

    const val SHORT = 120
    const val MEDIUM = 250
    const val LONG = 350

    /** Coil crossfade for artwork. 250 ms+ reads as lag when a row of covers fills in. */
    const val IMAGE_CROSSFADE = 180

    fun <T> enter(duration: Int = MEDIUM, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, EmphasizedDecelerate)

    fun <T> exit(duration: Int = SHORT): FiniteAnimationSpec<T> =
        tween(duration, 0, EmphasizedAccelerate)

    // ---- Navigation -------------------------------------------------------------------------

    /**
     * Tabs: Material "fade through". The old screen fades out quickly, the new one fades in with
     * a slight scale-up. Replaces NavHost's default 700 ms cross-fade, during which both screens
     * were drawn (and recorded for the glass blur) on every frame.
     */
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(210, 60, LinearOutSlowInEasing)) +
                scaleIn(tween(210, 60, LinearOutSlowInEasing), initialScale = 0.98f)
    }
    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(90, 0, FastOutSlowInEasing))
    }

    /** Push (Settings, History): a short slide from the trailing edge; pop mirrors it. */
    val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(LONG, 0, EmphasizedDecelerate)) { it / 5 } +
                fadeIn(tween(MEDIUM, 0, LinearOutSlowInEasing))
    }
    val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(LONG, 0, EmphasizedDecelerate)) { -it / 10 } +
                fadeOut(tween(MEDIUM, 0, Standard))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(LONG, 0, EmphasizedDecelerate)) { -it / 10 } +
                fadeIn(tween(MEDIUM, 0, LinearOutSlowInEasing))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(MEDIUM, 0, EmphasizedAccelerate)) { it / 5 } +
                fadeOut(tween(MEDIUM, 0, Standard))
    }

    /** Show page: a cross-fade under the shared cover/title (container transform). */
    val fadeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(LONG, 0, LinearOutSlowInEasing))
    }
    val fadeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(MEDIUM, 0, Standard))
    }

    /** Full Player: a sheet that rises from the bottom and drops back down. */
    val sheetEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInVertically(tween(LONG, 0, EmphasizedDecelerate)) { it } +
                fadeIn(tween(MEDIUM, 0, LinearOutSlowInEasing))
    }
    val sheetExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutVertically(tween(MEDIUM, 0, EmphasizedAccelerate)) { it } +
                fadeOut(tween(MEDIUM, 50, Standard))
    }

    /**
     * The screen under the Player fades for exactly as long as the sheet moves. The Player's
     * backdrop is the translucent blurred artwork, so the screen below cannot simply stay.
     */
    val underSheetExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(LONG, 0, Standard))
    }
    val underSheetPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MEDIUM, 0, LinearOutSlowInEasing))
    }
}

/**
 * False when the user turned animations off (Developer options or Accessibility "Remove
 * animations"). Compose already skips finite animations then; this is for the endless ones
 * (placeholder pulse) that would otherwise keep requesting frames.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
}
