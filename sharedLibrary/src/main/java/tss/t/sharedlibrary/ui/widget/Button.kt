package tss.t.sharedlibrary.ui.widget

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

object TSButtonDefaults {
    internal val _buttonHeight = 50.dp
    internal val _cornerRadius = 8.dp
    internal val _iconSize = 20.dp
    internal val ZeroPadding by lazy {
        PaddingValues(0.dp)
    }
    val CommonContentPadding by lazy {
        PaddingValues(horizontal = 16.dp)
    }
}

@Composable
fun TSButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    cornerRadius: Dp = TSButtonDefaults._cornerRadius,
    leftIcon: @Composable () -> Unit = {},
    rightIcon: @Composable () -> Unit = {},
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    Offset.Zero
    val interactiveSource = remember {
        MutableInteractionSource()
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(TSButtonDefaults._buttonHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(Colors.Primary, Colors.Secondary),
                    start = Offset(
                        0f,
                        Float.POSITIVE_INFINITY
                    ),
                    end = Offset(
                        Float.POSITIVE_INFINITY,
                        Float.POSITIVE_INFINITY
                    )
                )
            )
            .padding(contentPadding),
        interactionSource = interactiveSource,
        shape = RoundedCornerShape(cornerRadius),
        enabled = enable,
        colors = ButtonDefaults.buttonColors(
            containerColor = Colors.White.copy(alpha = 0f)
        )
    ) {
        leftIcon()
        Spacer(modifier = Modifier.size(4.dp))
        subTitle?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, style = titleTextStyle)
                Text(text = subTitle, style = TextStyles.Body4)
            }
        } ?: Text(text = title, style = titleTextStyle)
        Spacer(modifier = Modifier.size(4.dp))
        rightIcon()
    }
}


@Composable
fun TSButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    cornerRadius: Dp = TSButtonDefaults._cornerRadius,
    leftIcon: ImageVector? = null,
    rightIcon: ImageVector? = null,
    subTitle: String? = null,
    iconTintColor: Color = Color.White,
    rightIconTintColor: Color = Color.White,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    TSButton(
        modifier = modifier,
        title = title,
        enable = enable,
        cornerRadius = cornerRadius,
        onClick = onClick,
        subTitle = subTitle,
        leftIcon = {
            leftIcon?.let {
                Image(
                    imageVector = it, contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTintColor),
                    modifier = Modifier.size(TSButtonDefaults._iconSize)
                )
            }
        },
        rightIcon = {
            rightIcon?.let {
                Image(
                    imageVector = it, contentDescription = null,
                    colorFilter = ColorFilter.tint(rightIconTintColor),
                    modifier = Modifier.size(TSButtonDefaults._iconSize)
                )
            }
        },
        contentPadding = contentPadding
    )
}


@Composable
fun TSButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    cornerRadius: Dp = TSButtonDefaults._cornerRadius,
    @DrawableRes
    drawableLeft: Int = 0,
    @DrawableRes
    drawableRight: Int = 0,
    subTitle: String? = null,
    iconTintColor: Color = Color.White,
    rightIconTintColor: Color = Color.White,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    TSButton(
        modifier = modifier,
        title = title,
        enable = enable,
        cornerRadius = cornerRadius,
        onClick = onClick,
        subTitle = subTitle,
        leftIcon = {
            if (drawableLeft != 0) {
                Image(
                    painter = painterResource(id = drawableLeft), contentDescription = null,
                    colorFilter = ColorFilter.tint(iconTintColor)
                )
            }
        },
        rightIcon = {
            if (drawableRight != 0) {
                Image(
                    painter = painterResource(id = drawableRight), contentDescription = null,
                    colorFilter = ColorFilter.tint(rightIconTintColor)
                )
            }
        },
        contentPadding = contentPadding
    )
}


@Composable
fun TSRoundedButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    leftIcon: @Composable () -> Unit = {},
    rightIcon: @Composable () -> Unit = {},
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    TSButton(
        title = title,
        onClick = onClick,
        cornerRadius = 100.dp,
        subTitle = subTitle,
        titleTextStyle = titleTextStyle,
        enable = enable,
        modifier = modifier,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        contentPadding = contentPadding
    )
}

@Composable
fun TSRoundedButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    leftIcon: ImageVector? = null,
    rightIcon: ImageVector? = null,
    subTitle: String? = null,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    TSButton(
        title = title,
        onClick = onClick,
        cornerRadius = 100.dp,
        subTitle = subTitle,
        enable = enable,
        modifier = modifier,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        contentPadding = contentPadding
    )
}


@Composable
fun TSRoundedButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    @DrawableRes
    drawableLeft: Int = 0,
    @DrawableRes
    drawableRight: Int = 0,
    subTitle: String? = null,
    iconTintColor: Color = Color.White,
    rightIconTintColor: Color = Color.White,
    enable: Boolean = true,
    contentPadding: PaddingValues = TSButtonDefaults.ZeroPadding
) {
    TSButton(
        modifier = modifier,
        title = title,
        enable = enable,
        cornerRadius = 100.dp,
        onClick = onClick,
        subTitle = subTitle,
        drawableLeft = drawableLeft,
        drawableRight = drawableRight,
        iconTintColor = iconTintColor,
        rightIconTintColor = rightIconTintColor,
        contentPadding = contentPadding
    )
}


@Composable
fun TSOutlineButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    cornerRadius: Dp = TSButtonDefaults._cornerRadius,
    leftIcon: @Composable (() -> Unit)? = null,
    rightIcon: @Composable (() -> Unit)? = null,
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    borderSize: Dp = 1.dp
) {
    val interactiveSource = remember {
        MutableInteractionSource()
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(TSButtonDefaults._buttonHeight),
        interactionSource = interactiveSource,
        shape = RoundedCornerShape(cornerRadius),
        border = if (borderSize > 0.dp) BorderStroke(
            borderSize,
            color = Colors.ButtonColor
        ) else null,
        colors = ButtonDefaults.outlinedButtonColors().copy(
            containerColor = Colors.ButtonColorSecondary
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftIcon?.let {
                Box(modifier = Modifier.size(TSButtonDefaults._iconSize)) {
                    it()
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title, style = titleTextStyle,
                    color = Colors.ButtonColor
                )
                subTitle?.let { Text(text = it, style = TextStyles.Body4) }
            }
            rightIcon?.let {
                Box(modifier = Modifier.size(TSButtonDefaults._iconSize)) {
                    it()
                }
            }

        }
    }
}

@Composable
fun TSSecondaryButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    leftIcon: @Composable (() -> Unit)? = null,
    rightIcon: @Composable (() -> Unit)? = null,
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    cornerRadius: Dp = TSButtonDefaults._cornerRadius,
    borderSize: Dp = 0.dp,
) {
    TSOutlineButton(
        modifier = modifier,
        title = title,
        onClick = onClick,
        cornerRadius = cornerRadius,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        subTitle = subTitle,
        titleTextStyle = titleTextStyle,
        borderSize = borderSize,
    )
}

@Composable
fun TSOutlineRoundedButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    leftIcon: @Composable () -> Unit = {},
    rightIcon: @Composable () -> Unit = {},
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    borderSize: Dp = 1.dp
) {
    TSOutlineButton(
        modifier = modifier,
        title = title,
        onClick = onClick,
        cornerRadius = 100.dp,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        subTitle = subTitle,
        titleTextStyle = titleTextStyle,
        borderSize = borderSize
    )
}


@Composable
fun TSSecondaryRoundedButton(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
    leftIcon: @Composable () -> Unit = {},
    rightIcon: @Composable () -> Unit = {},
    subTitle: String? = null,
    titleTextStyle: TextStyle = TextStyles.Button3,
    borderSize: Dp = 0.dp
) {
    TSOutlineButton(
        modifier = modifier,
        title = title,
        onClick = onClick,
        cornerRadius = 100.dp,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        subTitle = subTitle,
        titleTextStyle = titleTextStyle,
        borderSize = borderSize
    )
}