package tss.t.podcast.ui.navigations

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainNavigation() {
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    }
}