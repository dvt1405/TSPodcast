package tss.t.podcast.ui.screens.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.hazeandroid.hazeChild
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

sealed class BottomBarTab(
    val title: String,
    val icon: ImageVector,
    val color: Color
) {
    data object Profile : BottomBarTab(
        title = "Favorite",
        icon = Icons.Rounded.Favorite,
        color = Color(0xFFFFA574)
    )

    data object Home : BottomBarTab(
        title = "TSPodcast",
        icon = Icons.Rounded.Home,
        color = Color(0xFFFA6FFF)
    )

    data object Settings : BottomBarTab(
        title = "Search",
        icon = Icons.Rounded.Search,
        color = Color(0xFFADFF64)
    )

    data object Radio : BottomBarTab(
        title = "Radio",
        icon = Icons.Rounded.Radio,
        color = Color(0xFFADFF64)
    )
}

internal val tabDefaults = listOf(
    BottomBarTab.Profile,
    BottomBarTab.Home,
    BottomBarTab.Radio,
    BottomBarTab.Settings,
)

@Composable
fun BottomBarTabs(
    tabs: List<BottomBarTab> = tabDefaults,
    selectedTab: Int = 1,
    onTabSelected: (BottomBarTab, Int) -> Unit = { _, _ -> },
    textColor: Color = Colors.White,
    contentColor: Color = Colors.White
) {
    CompositionLocalProvider(
        LocalTextStyle provides TextStyles.Title6.copy(color = textColor),
        LocalContentColor provides contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            for (tab in tabs.indices) {
                val alpha by animateFloatAsState(
                    targetValue = if (selectedTab == tab) 1f else .35f,
                    label = "alpha"
                )
                val scale by animateFloatAsState(
                    targetValue = if (selectedTab == tab) 1f else .95f,
                    visibilityThreshold = .000001f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                    label = "scale"
                )
                Column(
                    modifier = Modifier
                        .scale(scale)
                        .alpha(alpha)
                        .fillMaxHeight()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onTabSelected(tabs[tab], tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = tabs[tab].icon, contentDescription = tabs[tab].title)
                    Text(text = tabs[tab].title)
                }
            }
        }
    }
}

@Preview
@Composable
fun BottomAppBarPreview() {
    val hazeState = remember { HazeState() }
    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp, horizontal = 64.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.Transparent)
                    .hazeChild(state = hazeState)
                    .border(
                        width = Dp.Hairline,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Colors.Primary.copy(alpha = .8f),
                                Colors.Primary.copy(alpha = .2f),
                            ),
                        ),
                        shape = CircleShape
                    )
            ) {
                BottomBarTabs()
            }
        }
    ) { innerPaddings ->
        LazyColumn(
            modifier = Modifier
                .background(Colors.White)
                .haze(
                    hazeState,
                    HazeDefaults.style(
                        backgroundColor = Colors.Primary,
                        tint = Colors.White.copy(.3f),
                        blurRadius = 30.dp,
                    )
                )
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.size(innerPaddings.calculateTopPadding()))
            }
            items(100) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Colors.Primary)
                )
            }
        }
    }
}