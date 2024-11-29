package tss.t.podcast.ui.screens.discorver.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.request.ImageRequest
import tss.t.coreapi.models.Podcast
import tss.t.podcast.SharedElementKey
import tss.t.podcast.ui.screens.main.podcastDetailBoundsTransform
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.ShimmerAsyncImage
import tss.t.sharedresources.R

internal const val TrendingBoundKey = "TrendingBound"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TrendingWidget(
    modifier: Modifier = Modifier,
    podcast: Podcast = Podcast.default,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    shareBoundKey: String = TrendingBoundKey,
    onClick: Podcast.() -> Unit = {},
) {
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
                EnterExitState.PreEnter -> 10.dp
                EnterExitState.Visible -> 10.dp
                EnterExitState.PostExit -> 10.dp
            }
        }

    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(roundedCorner))
                .width(250.dp)
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
                        animatedVisibilityScope = animatedContentScope,
                        clipInOverlayDuringTransition = OverlayClip(
                            RoundedCornerShape(roundedCorner)
                        )
                    )
                    .clip(RoundedCornerShape(roundedCorner))
                    .width(250.dp)
                    .aspectRatio(1f),
            )

            Text(
                text = podcast.title ?: "",
                style = TextStyles.Title6,
                modifier = Modifier
                    .fillMaxWidth()
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
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Justify
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

@Composable
fun AsyncTrendingWidget(
    placeholderColor: Color
) {
    Column(
        modifier = Modifier.width(250.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(15.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
        )
        Spacer(modifier = Modifier.size(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor)
        )

    }
}

@Composable
@Preview
fun AsyncTrendingWidgetPreview() {
    AsyncTrendingWidget(Colors.Secondary50)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
fun TrendingWidgetPreview() {
    SharedTransitionLayout {
        AnimatedContent(
            true, label = "",
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.White)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(10) { index ->
                    TrendingWidget(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@AnimatedContent,
                        podcast = Podcast.default.copy(id = index.toLong()),
                    )
                }
            }
            if (it) {
            }
        }
    }
}
