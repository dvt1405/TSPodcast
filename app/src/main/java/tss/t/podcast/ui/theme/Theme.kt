package tss.t.podcast.ui.theme

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tss.t.podcast.LocalNavAnimatedVisibilityScope
import tss.t.podcast.LocalSharedTransitionScope
import tss.t.sharedfirebase.LocalAnalyticsScope
import tss.t.sharedfirebase.TSAnalytics
import tss.t.sharedfirebase.TSFirebaseSharedPref

@Composable
fun PodcastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor) {
        when {
//            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//            }
//            darkTheme -> darkColorScheme()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            else -> lightColorScheme()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PodcastThemePreview(
    content: @Composable () -> Unit
) {
    val scope = CoroutineScope(Dispatchers.Default)
    SharedTransitionScope {
        AnimatedContent(true, label = "") {
            if (it) {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionScope,
                    LocalNavAnimatedVisibilityScope provides this,
                    LocalAnalyticsScope provides TSAnalytics(
                        context = LocalContext.current,
                        sharedPref = TSFirebaseSharedPref(LocalContext.current, scope),
                        _analyticScope = scope
                    )
                ) {
                    content()
                }
            }
        }
    }
}