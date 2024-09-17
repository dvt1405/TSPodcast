package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tss.t.coreapi.models.TrendingPodcast
import tss.t.podcast.SharedElementKey
import tss.t.podcast.ui.screens.main.podcastDetailBoundsTransform
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.ShimmerAsyncImage
import tss.t.sharedlibrary.ui.shadow
import tss.t.sharedresources.R

internal const val TrendingBoundKey = "TrendingBound"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrendingWidget(
    modifier: Modifier = Modifier,
    podcast: TrendingPodcast = TrendingPodcast.default,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    shareBoundKey: String = TrendingBoundKey,
    onClick: TrendingPodcast.() -> Unit = {},
) {
    val backgroundColor by animatedContentScope.transition
        .animateColor(label = "") { state: EnterExitState ->
            when (state) {
                EnterExitState.PreEnter -> Color.Transparent
                EnterExitState.Visible -> Colors.White
                EnterExitState.PostExit -> Colors.White
            }
        }

    val interactiveSource = remember { MutableInteractionSource() }
    val pressed by interactiveSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f, label = "scale",
        animationSpec = spring(stiffness = 300f)
    ) {
    }

    val roundedCorner by animatedContentScope.transition
        .animateDp(label = "rounded corner") { enterExit: EnterExitState ->
            when (enterExit) {
                EnterExitState.PreEnter -> 20.dp
                EnterExitState.Visible -> 20.dp
                EnterExitState.PostExit -> 20.dp
            }
        }

    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(roundedCorner))
                .shadow(
                    Color(0xAA000000),
                    4.dp,
                    8.dp,
                    32.dp
                )
                .width(180.dp)
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(
                        key = SharedElementKey(
                            id = "${podcast.id}",
                            podcast = podcast,
                            type = SharedElementKey.Type.Background
                        )
                    ),
                    animatedVisibilityScope = animatedContentScope,
                    clipInOverlayDuringTransition = OverlayClip(
                        RoundedCornerShape(roundedCorner)
                    )
                )
                .background(backgroundColor, RoundedCornerShape(roundedCorner))
                .clickable(
                    interactionSource = interactiveSource,
                    indication = null
                ) {
                    onClick(podcast)
                }
                .padding(bottom = 16.dp),
        ) {
            ShimmerAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(podcast.image)
                    .placeholder(R.drawable.image_loader_place_holder_top)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    )
                    .error(R.drawable.image_loader_place_holder_top)
                    .crossfade(true)
                    .crossfade(250)
                    .memoryCacheKey(podcast.image)
                    .placeholderMemoryCacheKey(podcast.image)
                    .build(),
                contentDescription = podcast.image,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .sharedElement(
                        rememberSharedContentState(
                            SharedElementKey(
                                id = "${podcast.id}",
                                podcast = podcast,
                                type = SharedElementKey.Type.Image
                            )
                        ),
                        animatedVisibilityScope = animatedContentScope
                    )
                    .width(180.dp)
                    .aspectRatio(13f / 16),
            )

            Text(
                podcast.title, style = TextStyles.Title6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .sharedBounds(
                        rememberSharedContentState(
                            key = SharedElementKey(
                                id = "${podcast.id}",
                                podcast = podcast,
                                type = SharedElementKey.Type.Title
                            )
                        ),
                        animatedVisibilityScope = animatedContentScope,
                    )
                    .padding(top = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                buildAnnotatedString {
                    append(
                        HtmlCompat.fromHtml(
                            podcast.description ?: "",
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                }, style = TextStyles.Body4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .sharedBounds(
                        rememberSharedContentState(
                            key = SharedElementKey(
                                id = podcast.id,
                                podcast = podcast,
                                type = SharedElementKey.Type.Description
                            )
                        ),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = podcastDetailBoundsTransform
                    )
                    .padding(top = 2.dp),
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
fun TrendingWidgetPreview() {
    SharedTransitionLayout {
        AnimatedContent(true, label = "") {
            if (it) {
                TrendingWidget(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedContentScope = this@AnimatedContent
                )
            }
        }
    }
}
