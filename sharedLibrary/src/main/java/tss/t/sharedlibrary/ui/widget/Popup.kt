package tss.t.sharedlibrary.ui.widget

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

@Composable
fun TSPopup(
    title: String? = null,
    contentText: String,
    positiveText: String? = null,
    negativeText: String? = null,
    @DrawableRes image: Int? = null,
    onNegativeButtonClick: () -> Unit = {},
    onPositiveButtonClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .shadow(
                elevation = 8.dp, spotColor = Color(0x1A000000), ambientColor = Color(0x1A000000)
            )
            .background(color = Colors.White, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp)
            .wrapContentHeight()
            .defaultMinSize(minWidth = 200.dp)
    ) {
        if (title != null) {
            Text(
                title,
                style = TextStyles.H4,
                modifier = Modifier
                    .padding(bottom = 8.dp),
            )
        }
        Text(
            contentText,
            style = TextStyles.Caption2,
            modifier = Modifier
                .then(
                    if (positiveText != null) {
                        Modifier.padding(bottom = 16.dp)
                    } else {
                        Modifier
                    }
                ),
        )
        positiveText?.let {
            TSPopUpButtonHorizontal(
                positiveText,
                negativeText,
                onNegativeButtonClick,
                onPositiveButtonClick
            )
        }

    }
}

@Composable
private fun TSPopUpButtonHorizontal(
    positiveText: String,
    negativeText: String?,
    onNegativeButtonClick: () -> Unit,
    onPositiveButtonClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (negativeText != null) {
            TSSecondaryButton(
                title = negativeText,
                modifier = Modifier
                    .weight(1f),
                onClick = onNegativeButtonClick,
            )
        }
        TSButton(
            title = positiveText,
            modifier = Modifier.weight(1f),
            onClick = onPositiveButtonClick,
        )
    }
}