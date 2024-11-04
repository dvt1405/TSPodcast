package tss.t.sharedlibrary.ui.widget

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import tss.t.sharedlibrary.theme.Colors

object ToogleV24 {
    data class ToogleColors(
        val thumbCheckedColor: Color = Colors.Primary,
        val trackCheckedColor: Color = Colors.White,
        val thumbUnCheckedColor: Color = Colors.Gray20,
        val trackUnCheckedColor: Color = trackCheckedColor,
    )

    private val toogleColorsDefault by lazy { ToogleColors() }

    @Composable
    fun defaultColors(
        thumbCheckedColor: Color = Colors.Primary,
        trackCheckedColor: Color = Colors.White,
        thumbUnCheckedColor: Color = Colors.Gray20,
        trackUnCheckedColor: Color = trackCheckedColor,
    ) = toogleColorsDefault.copy(
        thumbCheckedColor = thumbCheckedColor,
        trackCheckedColor = trackCheckedColor,
        thumbUnCheckedColor = thumbUnCheckedColor,
        trackUnCheckedColor = trackUnCheckedColor
    )
}

/**
 * ToogleV24
 * @param checked Toggle status
 * @param active Toggle state
 * @param toggleSize Toggle size, one of ([ToogleV24Size.Big]|[ToogleV24Size.Medium]|[ToogleV24Size.Small]|[ToogleV24Size.XSmall])
 * @param onCheckedChange Checked change listener event
 * @see <a href="https://www.figma.com/design/YFU6T7LooR0JDqBbEnHuIU/LATEST-DESIGN-SYSTEM-2024?node-id=211-4237&node-type=frame&t=bYWxwnaXJBw2QL9N-0">Toogle/CheckBox V24</a>
 * */
@Composable
fun ToogleV24(
    checked: Boolean,
    active: Boolean,
    toggleSize: ToogleV24Size = ToogleV24Size.Big,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    val defaultMinSize = when (toggleSize) {
        ToogleV24Size.Big -> DpSize(60.dp, 32.dp)
        ToogleV24Size.Medium -> DpSize(44.dp, 24.dp)
        ToogleV24Size.Small -> DpSize(36.dp, 20.dp)
        ToogleV24Size.XSmall -> DpSize(28.dp, 16.dp)
    }

    val trackPadding = when (toggleSize) {
        ToogleV24Size.Big -> 4.dp
        ToogleV24Size.Medium -> 3.dp
        ToogleV24Size.Small -> 2.dp
        ToogleV24Size.XSmall -> 1.dp
    }

    ToogleV24(
        checked = checked,
        modifier = Modifier.size(defaultMinSize),
        trackPaddingDp = trackPadding,
        colors = if (active) ToogleV24.defaultColors()
        else ToogleV24.defaultColors(
            thumbCheckedColor = Colors.Gray20,
            trackCheckedColor = Colors.Gray40,
            thumbUnCheckedColor = Colors.Gray20
        ),
        onCheckedChange = onCheckedChange
    )
}

/**
 * ToogleV24
 * @param checked Toggle status
 * @param modifier Toggle Modifier
 * @param trackPaddingDp Padding in [Dp]
 * @param colors Toggle state colors [ToogleV24.defaultColors]
 * @param onCheckedChange Checked change listener event
 * @see <a href="https://www.figma.com/design/YFU6T7LooR0JDqBbEnHuIU/LATEST-DESIGN-SYSTEM-2024?node-id=211-4237&node-type=frame&t=bYWxwnaXJBw2QL9N-0">Toogle/CheckBox V24</a>
 * */
@Composable
fun ToogleV24(
    checked: Boolean,
    modifier: Modifier = Modifier.size(60.dp, 32.dp),
    trackPaddingDp: Dp = 4.dp,
    colors: ToogleV24.ToogleColors = ToogleV24.defaultColors(),
    onCheckedChange: (Boolean) -> Unit = {}
) {
    var checkedState by remember {
        mutableStateOf(checked)
    }
    val transition = updateTransition(checkedState, label = "Checked transition")
    val thumbColor by transition.animateColor(label = "ThumbColor") {
        if (it) {
            colors.thumbCheckedColor
        } else {
            colors.thumbUnCheckedColor
        }
    }
    val trackColor by transition.animateColor(label = "TrackColor") {
        if (it) {
            colors.trackCheckedColor
        } else {
            colors.trackUnCheckedColor
        }
    }
    val offsetFraction by transition.animateFloat(label = "TrackOffsetX") {
        if (it) {
            0f
        } else {
            1f
        }
    }
    val padding = with(LocalDensity.current) {
        trackPaddingDp.toPx()
    }
    var checkedStateOnDragStart by remember {
        mutableStateOf(checkedState)
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(1000.dp))
            .toggleable(
                value = checkedState,
                onValueChange = {
                    checkedState = it
                    onCheckedChange(checkedState)
                })
            .draggable(
                state = rememberDraggableState { delta ->
                    if (delta < -10f && checkedState) {
                        checkedState = false
                    }
                    if (delta > 10f && !checkedState) {
                        checkedState = true
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    checkedStateOnDragStart = checkedState
                },
                onDragStopped = {
                    if (checkedStateOnDragStart != checkedState) {
                        onCheckedChange(checkedState)
                    }
                }
            )
    ) {
        val radius = size.height / 2 - padding
        drawRoundRect(
            color = thumbColor,
            size = size,
            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        )
        drawCircle(
            color = trackColor,
            center = Offset(
                lerp(radius + padding, size.width - padding - radius, 1 - offsetFraction),
                center.y
            ),
            radius = radius
        )
    }
}

@Immutable
enum class ToogleV24Size {
    Big,
    Medium,
    Small,
    XSmall
}

@Composable
@Preview
fun ToogleV24Preview() {
    MaterialTheme() {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToogleV24(true, active = true)
                ToogleV24(true, active = true, toggleSize = ToogleV24Size.Medium)
                ToogleV24(true, active = true, toggleSize = ToogleV24Size.Small)
                ToogleV24(true, active = true, toggleSize = ToogleV24Size.XSmall)

                ToogleV24(true, active = false)
                ToogleV24(true, active = false, toggleSize = ToogleV24Size.Medium)
                ToogleV24(true, active = false, toggleSize = ToogleV24Size.Small)
                ToogleV24(true, active = false, toggleSize = ToogleV24Size.XSmall)

                ToogleV24(false, active = true)
                ToogleV24(false, active = true, toggleSize = ToogleV24Size.Medium)
                ToogleV24(false, active = true, toggleSize = ToogleV24Size.Small)
                ToogleV24(false, active = true, toggleSize = ToogleV24Size.XSmall)

                ToogleV24(false, active = false)
                ToogleV24(false, active = false, toggleSize = ToogleV24Size.Medium)
                ToogleV24(false, active = false, toggleSize = ToogleV24Size.Small)
                ToogleV24(false, active = false, toggleSize = ToogleV24Size.XSmall)

                ToogleV24(true, modifier = Modifier.size(120.dp, 60.dp))
                ToogleV24(
                    true,
                    modifier = Modifier.size(120.dp, 60.dp),
                    colors = ToogleV24.defaultColors(
                        thumbCheckedColor = Colors.Gray20,
                        trackCheckedColor = Colors.Gray40,
                        thumbUnCheckedColor = Colors.Gray20
                    )
                )
                ToogleV24(false, modifier = Modifier.size(120.dp, 60.dp))
                ToogleV24(
                    false,
                    modifier = Modifier.size(120.dp, 60.dp),
                    colors = ToogleV24.defaultColors(
                        thumbCheckedColor = Colors.Gray20,
                        trackCheckedColor = Colors.Gray40,
                        thumbUnCheckedColor = Colors.Gray20
                    )
                )
            }
        }

    }
}