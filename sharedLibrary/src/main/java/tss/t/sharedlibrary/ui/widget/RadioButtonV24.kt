package tss.t.sharedlibrary.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.R
import tss.t.sharedlibrary.theme.TextStyles

@Immutable
enum class RadioButtonSizeV24 {
    Big,
    Small
}

@Immutable
enum class RadioButtonTypeV24 {
    Normal,
    Check
}

@Composable
fun RadioButtonV24(
    text: String,
    checked: Boolean = false,
    active: Boolean = true,
    size: RadioButtonSizeV24 = RadioButtonSizeV24.Big,
    type: RadioButtonTypeV24 = RadioButtonTypeV24.Normal,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    val iconSizeDp = when (size) {
        RadioButtonSizeV24.Big -> CheckBoxV24.iconSizeBig
        RadioButtonSizeV24.Small -> CheckBoxV24.iconSizeSmall
    }

    val iconPaddingDp = when (size) {
        RadioButtonSizeV24.Big -> 2.dp
        RadioButtonSizeV24.Small -> 1.dp
    }

    val textStyle = when (size) {
        RadioButtonSizeV24.Big -> TextStyles.Body2
        RadioButtonSizeV24.Small -> TextStyles.Body3
    }

    val checkedIcon = when (type) {
        RadioButtonTypeV24.Check -> R.drawable.ic_glyph
        else -> R.drawable.ic_radio_button_selected
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
        iconShape = CircleShape,
        unCheckedIcon = R.drawable.ic_circle_stroke
    )
}

@Composable
@Preview
fun RadioButtonV24_Preview() {
    Column(
        modifier = Modifier.background(Color.White),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButtonV24("Test", checked = true, type = RadioButtonTypeV24.Check)
        RadioButtonV24("Test", checked = true)
        RadioButtonV24("Test", checked = true, active = false)
        RadioButtonV24(
            "Test",
            checked = false,
            size = RadioButtonSizeV24.Small,
            type = RadioButtonTypeV24.Check
        )
        RadioButtonV24("Test", checked = false, size = RadioButtonSizeV24.Small)
        RadioButtonV24("Test", checked = false, active = false, size = RadioButtonSizeV24.Small)
    }
}