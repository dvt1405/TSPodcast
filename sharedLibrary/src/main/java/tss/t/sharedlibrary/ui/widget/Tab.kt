package tss.t.sharedlibrary.ui.widget

import android.graphics.BlurMaskFilter
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles


object TSTabs {
    @Immutable
    data class TabColors(
        val defaultTintColor: Color = Colors.Gray60,
        val selectedTintColor: Color = Colors.Primary,
        val backgroundSelectedColor: Color = Colors.Primary10,
        val backgroundSelectedBadgeColor: Color = Colors.Primary20,
        val selectedBadgeColor: Color = Colors.Primary60,
    )

    val color by lazy { TabColors() }
    val tabColors: TabColors
        get() = color

    val bigPaddingValues by lazy { PaddingValues(horizontal = 16.dp, vertical = 12.dp) }
    val mediumPaddingValues by lazy { PaddingValues(horizontal = 12.dp, vertical = 8.dp) }
    val smallPaddingValues by lazy { PaddingValues(horizontal = 8.dp, vertical = 6.dp) }
    val bigRadius by lazy { 10.dp }
    val mediumRadius by lazy { 8.dp }
    val smallRadius by lazy { 6.dp }
}

@Immutable
data class TabData(
    val text: String, @DrawableRes val iconRes: Int = 0
)

@Immutable
enum class TSTabStyles {
    Normal, Underline, DropShadow
}

@Immutable
enum class TSTabSize {
    Small, Medium, Big
}

@Immutable
enum class TSTabMode {
    Fixed, Scrollable
}

@Composable
fun Tabs(
    text: String,
    @DrawableRes iconRes: Int = 0,
    modifier: Modifier,
    tabSize: TSTabSize = TSTabSize.Big,
    isSelected: Boolean,
    onTabSelected: (offset: Float, size: IntSize) -> Unit = { _, _ -> }
) {
    val tintColor by animateColorAsState(
        if (isSelected) TSTabs.tabColors.selectedTintColor else TSTabs.tabColors.defaultTintColor,
        label = "TabTintColor"
    )
    var calculatedTabSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    var offset by remember {
        mutableFloatStateOf(0f)
    }

    val paddingValues = remember(tabSize) {
        when (tabSize) {
            TSTabSize.Big -> TSTabs.bigPaddingValues
            TSTabSize.Medium -> TSTabs.mediumPaddingValues
            TSTabSize.Small -> TSTabs.smallPaddingValues
        }
    }

    val textStyle = remember(isSelected, tabSize) {
        when {
            isSelected && tabSize == TSTabSize.Small -> TextStyles.SubTitle3
            !isSelected && tabSize == TSTabSize.Small -> TextStyles.Body4
            isSelected -> TextStyles.SubTitle2
            else -> TextStyles.Body3
        }
    }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            onTabSelected(offset, calculatedTabSize)
        }
    }

    Row(
        modifier = modifier
            .onGloballyPositioned {
                calculatedTabSize = it.size
                offset = it.positionInRoot().x
                if (isSelected) {
                    onTabSelected(offset, calculatedTabSize)
                }
            }
            .padding(paddingValues)
            .onSizeChanged { calculatedTabSize = it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        iconRes.takeIf { it != 0 }?.let {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Tab $text",
                tint = tintColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = textStyle.copy(tintColor),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tab
 * @param tabs List of [TabData] with text and left icon
 * @param tabSize either [TSTabSize.Big] or [TSTabSize.Medium] or [TSTabSize.Small], default value is [TSTabSize.Big]
 * @param tabStyle either [TSTabStyles.Normal] or [TSTabStyles.Underline] or [TSTabStyles.DropShadow], default value is [TSTabStyles.Normal]
 * @param tsTabMode if [tsTabMode.Fixed] size of child tab will be calculated based on size of [tabs]. If [tsTabMode.Scrollable] size of child tabs will be wrap content
 * @param onTabSelected listener for tab selected
 * @see <a href="https://www.figma.com/design/YFU6T7LooR0JDqBbEnHuIU/LATEST-DESIGN-SYSTEM-2024?node-id=329-1963&node-type=frame&t=o151XVqkUorERxNf-0">Tabs</a>
 * */
@Composable
fun TabGroupV24(
    tabs: List<TabData>,
    modifier: Modifier = Modifier,
    tabSize: TSTabSize = TSTabSize.Big,
    tabStyle: TSTabStyles = TSTabStyles.Normal,
    tsTabMode: TSTabMode = TSTabMode.Fixed,
    onTabSelected: (tabSelected: Int) -> Unit = {}
) {
    val tabHeight = remember(tabSize) {
        when (tabSize) {
            TSTabSize.Big -> 48.dp
            TSTabSize.Medium -> 40.dp
            TSTabSize.Small -> 32.dp
        }
    }

    val tabCount = remember(tabs.size) {
        tabs.size
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var selectedOffset by remember {
        mutableFloatStateOf(0f)
    }

    var selectedTabSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    val selectedTopLeft by animateFloatAsState(selectedOffset, label = "")
    val animateSelectedTabWidth by animateFloatAsState(selectedTabSize.width.toFloat(), label = "")

    val density = LocalDensity.current
    val tabRadiusSize = remember(tabSize) {
        with(density) {
            when (tabSize) {
                TSTabSize.Big -> TSTabs.bigRadius.toPx()
                TSTabSize.Medium -> TSTabs.mediumRadius.toPx()
                TSTabSize.Small -> TSTabs.smallRadius.toPx()
            }
        }
    }
    val cornerRadius = remember(tabRadiusSize) {
        CornerRadius(tabRadiusSize)
    }

    Row(
        modifier = Modifier
            .heightIn(tabHeight)
            .drawBehind {
                when (tabStyle) {
                    TSTabStyles.Normal -> {
                        drawRoundRect(
                            TSTabs.tabColors.backgroundSelectedColor,
                            size = Size(
                                width = animateSelectedTabWidth,
                                height = size.height
                            ),
                            topLeft = Offset(selectedTopLeft, 0f),
                            cornerRadius = cornerRadius
                        )
                    }

                    TSTabStyles.Underline -> {
                        drawRect(
                            TSTabs.tabColors.selectedTintColor,
                            size = Size(
                                width = animateSelectedTabWidth,
                                height = 1.5.dp.toPx()
                            ),
                            topLeft = Offset(x = selectedTopLeft, y = size.height - 1.5.dp.toPx()),
                        )
                    }

                    else -> {
                        val blur = 2.dp.toPx()
                        drawIntoCanvas { canvas ->
                            val paint = Paint()
                            val frameworkPaint = paint.asFrameworkPaint()
                            frameworkPaint.maskFilter =
                                (BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL))
                            frameworkPaint.color = Colors.Black
                                .copy(0.1f)
                                .toArgb()
                            val leftPixel = selectedTopLeft - blur / 2
                            val topPixel = blur
                            val rightPixel = animateSelectedTabWidth + leftPixel + blur
                            val bottomPixel = size.height + topPixel
                            canvas.drawRoundRect(
                                left = leftPixel,
                                top = topPixel,
                                right = rightPixel,
                                bottom = bottomPixel,
                                radiusX = tabRadiusSize,
                                radiusY = tabRadiusSize,
                                paint = paint,
                            )
                            drawRoundRect(
                                Colors.White,
                                size = Size(
                                    width = animateSelectedTabWidth,
                                    height = size.height
                                ),
                                topLeft = Offset(selectedTopLeft, 0f),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }
            .then(modifier)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(tabCount) { tabIndex ->
            val tab = tabs[tabIndex]
            Tabs(
                text = tab.text,
                modifier = (if (tsTabMode == TSTabMode.Scrollable) Modifier else
                    Modifier.weight(1f / tabCount))
                    .clickable {
                        selectedTab = tabIndex
                        onTabSelected(tabIndex)
                    },
                iconRes = tab.iconRes,
                tabSize = tabSize,
                isSelected = selectedTab == tabIndex,
                onTabSelected = { offset, size ->
                    selectedOffset = offset
                    selectedTabSize = size
                }
            )
        }
    }
}

@Composable
@Preview
fun TabGroup_Preview() {
    Column(Modifier.background(Color.White)) {
        TabGroupV24(
            tabs = listOf(
                TabData("Dashboard"),
                TabData("My Account"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
            ),
            modifier = Modifier.fillMaxWidth(),
            tsTabMode = TSTabMode.Scrollable
        ) { }
        Spacer(Modifier.size(16.dp))
        TabGroupV24(
            tabs = listOf(
                TabData("Dashboard"),
                TabData("My Account"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
                TabData("Company"),
            ),
            modifier = Modifier.fillMaxWidth(),
            tabStyle = TSTabStyles.Underline,
            tsTabMode = TSTabMode.Scrollable,
            tabSize = TSTabSize.Medium
        ) { }
        Spacer(Modifier.size(16.dp))
        TabGroupV24(
            tabs = listOf(
                TabData("Dashboard"),
                TabData("My Account"),
                TabData("Company"),
            ),
            modifier = Modifier.fillMaxWidth(),
            tabStyle = TSTabStyles.Underline,
            tsTabMode = TSTabMode.Fixed,
            tabSize = TSTabSize.Small
        ) { }
        Spacer(Modifier.size(16.dp))
        TabGroupV24(
            tabs = listOf(
                TabData("Dashboard"),
                TabData("My Account"),
                TabData("Company"),
            ),
            modifier = Modifier.fillMaxWidth(),
            tsTabMode = TSTabMode.Fixed
        ) { }
        Spacer(Modifier.size(16.dp))
        TabGroupV24(
            tabs = listOf(
                TabData("Dashboard"),
                TabData("My Account"),
                TabData("Company"),
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            tsTabMode = TSTabMode.Scrollable,
            tabStyle = TSTabStyles.DropShadow,
            tabSize = TSTabSize.Medium
        ) { }
        Spacer(Modifier.size(16.dp))
    }
}