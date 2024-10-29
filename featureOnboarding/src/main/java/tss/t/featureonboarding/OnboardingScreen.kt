package tss.t.featureonboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tbuonomo.viewpagerdotsindicator.compose.DotsIndicator
import com.tbuonomo.viewpagerdotsindicator.compose.model.DotGraphic
import com.tbuonomo.viewpagerdotsindicator.compose.type.ShiftIndicatorType
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSRoundedButton

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel
) {
    val listItem by viewModel.uiState.collectAsState()
    DisposableEffect(Unit) {
        onDispose {
        }
    }
    Scaffold { paddingValues: PaddingValues ->
        OnboardingScreen(
            modifier,
            items = listItem.listItem,
            paddingValues,
            onOnboardingDisposed = {
                viewModel.onOnboardingDisposed()
            },
            onPageChanged = {
                viewModel.onPageChanged(it)
            },
            onShowed = {
                viewModel.onShowed()
            },
            onFinishOnBoarding = {
                viewModel.onFinishOnboarding()
            }
        )
    }
}

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    items: List<OnboardingViewModel.PageData> = OnboardingViewModel.defaultItems,
    paddingValues: PaddingValues = PaddingValues(),
    isSeparatedColor: Boolean = true,
    onPageChanged: (Int) -> Unit = { _ -> },
    onShowed: () -> Unit = {},
    onOnboardingDisposed: () -> Unit = {},
    onFinishOnBoarding: () -> Unit = {}
) {
    val pager = rememberPagerState(0) {
        items.size
    }

    var selectedItem by remember {
        mutableIntStateOf(0)
    }

    var continuaBtn by remember {
        mutableStateOf(false)
    }

    val alpha by animateFloatAsState(if (continuaBtn) 1f else 0f, label = "") {
    }
    LaunchedEffect(pager) {
        snapshotFlow {
            pager.currentPage
        }.collect {
            selectedItem = it
            continuaBtn = selectedItem == items.size - 1
            onPageChanged.invoke(it)
        }
    }
    LaunchedEffect(Unit) {
        onShowed()
    }
    DisposableEffect(Unit) {
        onDispose {
            onOnboardingDisposed()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isSeparatedColor) {
                            val brush = items[it].brush
                            if (brush != null) {
                                Modifier.background(brush)
                            } else {
                                Modifier.background(items[it].backgroundColor)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    buildAnnotatedString {
                        val title = stringResource(items[it].title)
                        append(title)
                        stringArrayResource(items[it].highlight).forEach { hlItem ->
                            val start = title.indexOf(hlItem)
                            val end = start + hlItem.length
                            addStyle(
                                TextStyles.Title1
                                    .copy(Colors.Primary)
                                    .toSpanStyle(), start, end
                            )
                        }
                    },
                    style = TextStyles.Title1.copy(Color(0xFF39375B)),
                    modifier = Modifier.padding(
                        top = paddingValues.calculateTopPadding() + 20.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
                )

                Image(
                    painter = painterResource(items[it].drawableRes), contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = paddingValues.calculateBottomPadding())
                .padding(bottom = 20.dp)
                .height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotsIndicator(
                dotCount = pager.pageCount,
                type = ShiftIndicatorType(dotsGraphic = DotGraphic(color = Colors.Primary)),
                pagerState = pager,
                modifier = Modifier
                    .weight(1f),
            )
            AnimatedVisibility(continuaBtn) {
                TSRoundedButton(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = (1 - alpha) * size.width
                        }
                        .padding(end = 16.dp)
                        .alpha(1f),
                    "Continue",
                    onClick = {
                        onFinishOnBoarding()
                    }
                )
            }
        }
    }
}

@Composable
fun PageIndicatorView(
    isSelected: Boolean,
    selectedColor: Color,
    defaultColor: Color,
    defaultRadius: Dp,
    selectedLength: Dp,
    animationDurationInMillis: Int,
    modifier: Modifier = Modifier,
) {
    val color: Color by animateColorAsState(
        targetValue = if (isSelected) {
            selectedColor
        } else {
            defaultColor
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        ), label = ""
    )

    val width: Dp by animateDpAsState(
        targetValue = if (isSelected) {
            selectedLength
        } else {
            defaultRadius
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        ), label = ""
    )

    Canvas(
        modifier = modifier
            .size(
                width = width,
                height = defaultRadius,
            ),
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(
                width = width.toPx(),
                height = defaultRadius.toPx(),
            ),
            cornerRadius = CornerRadius(
                x = defaultRadius.toPx(),
                y = defaultRadius.toPx(),
            ),
        )
    }
}


@Composable
@Preview
fun OnboardingPreview() {
    Surface {
        OnboardingScreen(items = OnboardingViewModel.defaultItems)
    }
}


@Composable
@Preview
fun PageIndicatorViewPreview() {
    val selected = 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (i in 1..3) {
            PageIndicatorView(
                selected == i,
                Colors.ButtonColor,
                Colors.ButtonColorSecondary,
                20.dp,
                animationDurationInMillis = 300,
                selectedLength = 60.dp,
            )
        }
    }
}