package tss.t.sharedlibrary.ui.widget

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

object CheckBoxV24 {
    val iconSizeBig = 24.dp
    val iconSizeSmall = 20.dp

    data class CheckBoxColors(
        val iconCheckedColor: Color = Colors.Primary,
        val iconUnCheckedColor: Color = Colors.Gray60,
        val textCheckedColor: Color = Colors.Gray70,
        val textUnCheckedColor: Color = textCheckedColor,
        val textDisableColor: Color = Colors.Gray50,
        val iconDisableColor: Color = Colors.Gray40,
    )

    private val checkBoxColors by lazy { CheckBoxColors() }

    @Composable
    fun defaultColors(
        iconCheckedColor: Color = Colors.Primary,
        iconUnCheckedColor: Color = Colors.Gray60,
        textCheckedColor: Color = Colors.Gray70,
        textUnCheckedColor: Color = textCheckedColor,
        textDisableColor: Color = Colors.Gray50,
        iconDisableColor: Color = Colors.Gray40,
    ) = checkBoxColors.copy(
        iconCheckedColor = iconCheckedColor,
        textCheckedColor = textCheckedColor,
        iconUnCheckedColor = iconUnCheckedColor,
        textUnCheckedColor = textUnCheckedColor,
        textDisableColor = textDisableColor,
        iconDisableColor = iconDisableColor
    )
}

@Immutable
enum class CheckBoxSizeV24 {
    Big,
    Small
}

@Immutable
enum class CheckBoxTypeV24 {
    Normal,
    Indeterminate
}

@Composable
fun CheckBoxV24(
    text: String,
    checked: Boolean = false,
    active: Boolean = true,
    size: CheckBoxSizeV24 = CheckBoxSizeV24.Big,
    type: CheckBoxTypeV24 = CheckBoxTypeV24.Normal,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    val iconSizeDp = when (size) {
        CheckBoxSizeV24.Big -> CheckBoxV24.iconSizeBig
        CheckBoxSizeV24.Small -> CheckBoxV24.iconSizeSmall
    }

    val iconPaddingDp = when (size) {
        CheckBoxSizeV24.Big -> 2.dp
        CheckBoxSizeV24.Small -> 1.dp
    }

    val textStyle = when (size) {
        CheckBoxSizeV24.Big -> TextStyles.Body2
        CheckBoxSizeV24.Small -> TextStyles.Body3
    }

    val checkedIcon = when (type) {
        CheckBoxTypeV24.Indeterminate -> R.drawable.ic_subtract
        else -> R.drawable.ic_checkbox_checked
    }

    val unCheckedIcon = when (type) {
        CheckBoxTypeV24.Indeterminate -> R.drawable.ic_checkbox_unchecked
        else -> R.drawable.ic_checkbox_unchecked
    }

    CheckBoxV24(
        text = text,
        modifier = Modifier,
        checked = checked,
        active = active,
        iconSizeDp = iconSizeDp,
        iconPaddingDp = iconPaddingDp,
        textStyle = textStyle,
        onCheckedChange = onCheckedChange,
        checkedIcon = checkedIcon,
        unCheckedIcon = unCheckedIcon
    )

}

@Composable
fun CheckBoxV24(
    text: String,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    active: Boolean = true,
    @DrawableRes
    checkedIcon: Int = R.drawable.ic_checkbox_checked,
    @DrawableRes
    unCheckedIcon: Int = R.drawable.ic_checkbox_unchecked,
    iconSizeDp: Dp = 24.dp,
    iconPaddingDp: Dp = 2.dp,
    textStyle: TextStyle = TextStyles.Body2,
    colors: CheckBoxV24.CheckBoxColors = CheckBoxV24.defaultColors(),
    iconShape: Shape = RectangleShape,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    var checkedState by remember {
        mutableStateOf(checked)
    }
    val updateState = updateTransition(checkedState to active, label = "CheckedState")
    val iconColor by updateState.animateColor(label = "IconColor") {
        val isChecked = it.first
        val isActive = it.second
        when {
            !isActive -> colors.iconDisableColor
            isChecked -> colors.iconCheckedColor
            else -> colors.iconUnCheckedColor
        }
    }
    val textColor by updateState.animateColor(label = "TextColor") {
        val isChecked = it.first
        val isActive = it.second
        when {
            !isActive -> colors.textDisableColor
            isChecked -> colors.textCheckedColor
            else -> colors.textUnCheckedColor
        }
    }


    Row(
        modifier = modifier.clickable(indication = null,
            interactionSource = remember { MutableInteractionSource() }) {
            checkedState = !checkedState
            onCheckedChange(checkedState)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(checkedState, label = "") {
            Icon(
                painter = if (it) painterResource(checkedIcon)
                else painterResource(unCheckedIcon),
                tint = iconColor,
                contentDescription = "",
                modifier = Modifier
                    .clip(shape = iconShape)
                    .toggleable(checkedState, onValueChange = {
                        checkedState = it
                        onCheckedChange(it)
                    })
                    .size(iconSizeDp)
                    .padding(iconPaddingDp)
            )
        }

        Text(
            text = text,
            style = textStyle.copy(color = textColor),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@Preview
fun CheckBoxV24_Preview() {
    MaterialTheme {
        Column(
            modifier = Modifier.background(Color.White),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CheckBoxV24("Test", checked = true, type = CheckBoxTypeV24.Indeterminate)
            CheckBoxV24("Test", checked = true)
            CheckBoxV24("Test", checked = false, active = false)
            CheckBoxV24(
                "Test",
                checked = true,
                size = CheckBoxSizeV24.Small,
                type = CheckBoxTypeV24.Indeterminate
            )
            CheckBoxV24("Test", checked = true, size = CheckBoxSizeV24.Small)
            CheckBoxV24("Test", checked = false, active = true, size = CheckBoxSizeV24.Small)
        }
    }
}