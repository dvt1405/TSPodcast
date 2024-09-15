package tss.t.podcast.ui.screens.podcastsdetail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import tss.t.coreapi.models.TrendingPodcast
import tss.t.hazeandroid.HazeDefaults
import tss.t.hazeandroid.HazeState
import tss.t.hazeandroid.haze
import tss.t.hazeandroid.hazeChild
import tss.t.podcast.ui.screens.MainViewModel
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PodcastDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    podcast: TrendingPodcast,
    mainViewModel: MainViewModel,
    podcastViewModel: PodcastViewModel = viewModel()
) {
    DisposableEffect(Unit) {
        onDispose {
        }
    }
    val hazeState = remember { HazeState() }
    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                    modifier = Modifier
                        .zIndex(1f),
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent),
                    actions = {},
                    title = {
                        Text(
                            podcast.title,
                            style = TextStyles.Title5,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        Image(
                            Icons.Rounded.ArrowBack,
                            "Back",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.Black.copy(0.3f))
                                .clickable {
                                    mainViewModel.popBackStack()
                                }
                                .padding(12.dp),
                            colorFilter = ColorFilter.tint(Colors.White)
                        )
                    }
                )

                AsyncImage(
                    podcast.image, podcast.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10)
                        .blur(50.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    ) { innerPadding ->
        Box {
            LazyColumn(
                modifier = Modifier.haze(hazeState)
            ) {
                item(key = "PaddingTop") {
                    Spacer(modifier = Modifier.size(innerPadding.calculateTopPadding()))
                }

                items(100) {
                    Box(modifier = Modifier.size(100.dp)) { }
                }

            }
        }
    }
}