package tss.t.featureonboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.theme.TextStyles
import kotlin.math.absoluteValue

/** Brand tones ("Lên sóng", brand/BRAND.md): Xanh TS, Xanh Đêm, Cam San Hô, the light blue of "TS" on dark. */
internal object OnboardingColors {
    val Blue = Color(0xFF184BB4)
    val Bright = Color(0xFF3D7BFF)
    val Night = Color(0xFF0B1A4A)
    val Coral = Color(0xFFFF7A59)
    val LightBlue = Color(0xFF8FB0FF)
    val Background = Brush.verticalGradient(listOf(Blue, Night))
}

private const val PRIVACY_URL = "https://podcast-25c93.web.app/privacy"

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingScreen(
        modifier = modifier,
        items = state.listItem,
        onPageChanged = viewModel::onPageChanged,
        onShowed = viewModel::onShowed,
        onFinishOnBoarding = { analytics, crash -> viewModel.onFinishOnboarding(analytics, crash) },
    )
}

/**
 * Intro in the brand tones: every page on the Xanh TS → Xanh Đêm gradient, the illustration in a
 * rounded card, a white title with the key words in light blue, a coral page indicator. The last
 * page asks for privacy consent (Nghị định 13/2023/NĐ-CP, Luật BVDLCN 91/2025): each purpose is a
 * separate switch, off by default, explained in plain words, and only an explicit tap saves it.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    items: List<OnboardingViewModel.PageData> = OnboardingViewModel.defaultItems,
    onPageChanged: (Int) -> Unit = {},
    onShowed: () -> Unit = {},
    onFinishOnBoarding: (analytics: Boolean, crashReports: Boolean) -> Unit = { _, _ -> },
) {
    val pageCount = items.size + 1 // + privacy
    val pager = rememberPagerState(0) { pageCount }
    val scope = rememberCoroutineScope()
    val isPrivacyPage = pager.currentPage == pageCount - 1

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { onPageChanged(it) }
    }
    LaunchedEffect(Unit) { onShowed() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
    ) {
        // Soft glow behind the content.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(OnboardingColors.Bright.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width
                ),
                radius = size.width,
                center = Offset(size.width * 0.8f, size.height * 0.2f)
            )
        }
        Column(Modifier.fillMaxSize()) {
            Header(
                showSkip = !isPrivacyPage,
                onSkip = { scope.launch { pager.animateScrollToPage(pageCount - 1) } }
            )
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                if (page < items.size) {
                    IntroPage(items[page], pager, page)
                } else {
                    PrivacyPage(onDecide = onFinishOnBoarding)
                }
            }
            if (!isPrivacyPage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(pageCount) { index ->
                            PageIndicatorView(
                                isSelected = pager.currentPage == index,
                                selectedColor = OnboardingColors.Coral,
                                defaultColor = Color.White.copy(alpha = 0.3f),
                                defaultRadius = 8.dp,
                                selectedLength = 24.dp,
                                animationDurationInMillis = 250,
                            )
                        }
                    }
                    Button(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = OnboardingColors.Blue
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(stringResource(R.string.onboarding_next), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(color = OnboardingColors.LightBlue))
                append("TS")
                pop()
                append("Podcast")
            },
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f)
        )
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip), color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun IntroPage(item: OnboardingViewModel.PageData, pager: PagerState, page: Int) {
    // Parallax: the card drifts and shrinks a little as the page slides away.
    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        val title = stringResource(item.title)
        val highlights = stringArrayResource(item.highlight)
        Text(
            buildAnnotatedString {
                append(title)
                highlights.forEach { phrase ->
                    val start = title.indexOf(phrase)
                    if (start >= 0) {
                        addStyle(SpanStyle(color = OnboardingColors.LightBlue), start, start + phrase.length)
                    }
                }
            },
            style = TextStyles.Title1.copy(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - offset * 0.6f; translationX = offset * 40f }
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .graphicsLayer {
                    val scale = 1f - offset * 0.08f
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = OnboardingColors.Night)
                .clip(RoundedCornerShape(28.dp))
                .background(item.brush ?: Brush.verticalGradient(listOf(item.backgroundColor, item.backgroundColor)))
        ) {
            Image(
                painter = painterResource(item.drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The privacy step. What the app does NOT collect, what stays on the phone, then one switch per
 * purpose (who processes it and why), off by default. "Đồng ý tất cả" or "Lưu lựa chọn" both save
 * an explicit answer; the app works fully either way.
 */
@Composable
private fun PrivacyPage(onDecide: (analytics: Boolean, crashReports: Boolean) -> Unit) {
    var analytics by rememberSaveable { mutableStateOf(false) }
    var crash by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.privacy_title), style = TextStyles.Title2.copy(color = Color.White))
        Text(stringResource(R.string.privacy_intro), style = TextStyles.Body3.copy(color = Color.White.copy(alpha = 0.9f)))

        InfoCard(
            title = stringResource(R.string.privacy_not_collected_title),
            body = stringResource(R.string.privacy_not_collected_body)
        )
        InfoCard(
            title = stringResource(R.string.privacy_on_device_title),
            body = stringResource(R.string.privacy_on_device_body)
        )

        Text(
            stringResource(R.string.privacy_choices_title),
            style = TextStyles.Title5.copy(color = Color.White),
            modifier = Modifier.padding(top = 4.dp)
        )
        ConsentSwitch(
            title = stringResource(R.string.privacy_analytics_title),
            body = stringResource(R.string.privacy_analytics_body),
            checked = analytics,
            onChange = { analytics = it }
        )
        ConsentSwitch(
            title = stringResource(R.string.privacy_crash_title),
            body = stringResource(R.string.privacy_crash_body),
            checked = crash,
            onChange = { crash = it }
        )
        Text(
            stringResource(R.string.privacy_rights),
            style = TextStyles.Body4.copy(color = Color.White.copy(alpha = 0.85f))
        )
        Text(
            stringResource(R.string.privacy_read_policy),
            style = TextStyles.Body3.copy(
                color = OnboardingColors.LightBlue,
                textDecoration = TextDecoration.Underline
            ),
            modifier = Modifier.clickable(role = Role.Button) { runCatching { uriHandler.openUri(PRIVACY_URL) } }
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onDecide(true, true) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OnboardingColors.Blue),
            shape = RoundedCornerShape(50)
        ) {
            Text(stringResource(R.string.privacy_accept_all), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { onDecide(analytics, crash) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f))
        ) {
            Text(
                stringResource(if (analytics || crash) R.string.privacy_save_choices else R.string.privacy_continue_without),
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            stringResource(R.string.privacy_footer),
            style = TextStyles.Body5.copy(color = Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(OnboardingColors.Coral)
            )
            Spacer(Modifier.size(8.dp))
            Text(title, style = TextStyles.Title6.copy(color = Color.White))
        }
        Text(body, style = TextStyles.Body4.copy(color = Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
private fun ConsentSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val border by animateColorAsState(
        if (checked) OnboardingColors.LightBlue else Color.White.copy(alpha = 0.15f),
        label = "consentBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (checked) 0.16f else 0.08f))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = TextStyles.Title6.copy(color = Color.White))
            Text(body, style = TextStyles.Body5.copy(color = Color.White.copy(alpha = 0.8f)))
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OnboardingColors.Coral,
                uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.4f)
            )
        )
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
        targetValue = if (isSelected) selectedColor else defaultColor,
        animationSpec = tween(durationMillis = animationDurationInMillis), label = ""
    )
    val width: Dp by animateDpAsState(
        targetValue = if (isSelected) selectedLength else defaultRadius,
        animationSpec = tween(durationMillis = animationDurationInMillis), label = ""
    )
    Canvas(modifier = modifier.size(width = width, height = defaultRadius)) {
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(width.toPx(), defaultRadius.toPx()),
            cornerRadius = CornerRadius(defaultRadius.toPx(), defaultRadius.toPx()),
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
