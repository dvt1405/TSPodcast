package tss.t.sharedlibrary.ui.widget

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

@Composable
fun TSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onTrailingIconClick: (() -> Unit)? = null,
    height: Dp = TextFieldDefaults.MinHeight,
    minHeight: Dp = TextFieldDefaults.MinHeight,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    helperText: String? = null,
    errorText: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isRequired: Boolean = false,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val bgColor = if (enabled) if (isError) Colors.Red10 else Colors.White else Colors.Gray15
    val colors = TextFieldDefaults.colors(
        disabledIndicatorColor = Colors.Gray15,
        focusedIndicatorColor = Colors.Primary,
        unfocusedIndicatorColor = Colors.Gray30,
        unfocusedContainerColor = bgColor,
        unfocusedLabelColor = Colors.Gray50,
        focusedLabelColor = Colors.Gray50,
        disabledLabelColor = Colors.Gray50,
        cursorColor = Colors.Primary,
        errorIndicatorColor = Colors.Red50,
        errorCursorColor = Colors.Red50,
        focusedContainerColor = Colors.Primary.copy(alpha = 0.1f)
    )
    val mergedTextStyle = TextStyles.Body2.copy(
        fontWeight = FontWeight.W500,
        color = Colors.Gray90,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val modifier =
        if (singleLine)
            Modifier.height(TextFieldDefaults.MinHeight)
        else if (height != TextFieldDefaults.MinHeight)
            Modifier.height(height)
        else Modifier
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .defaultMinSize(
                minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight
            ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = @Composable { innerTextField ->
            TSFieldDecorationBox(
                value = value,
                label = label,
                isRequired = isRequired,
                content = innerTextField,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                onTrailingIconClick = onTrailingIconClick,
                visualTransformation = visualTransformation,
                placeholder = placeholder,
                interactionSource = interactionSource,
                colors = colors,
                isError = isError,
                helperText = helperText,
                errorText = errorText,
                enabled = enabled,
                minHeight = minHeight,
                shape = shape
            )
        })
}

@Composable
fun TSFieldDecorationBox(
    value: String,
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation,
    label: String? = null,
    placeholder: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: InteractionSource,
    colors: TextFieldColors,
    helperText: String?,
    errorText: String?,
    minHeight: Dp,
    content: @Composable () -> Unit,
    isRequired: Boolean,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    var extraLabelSize by remember {
        mutableFloatStateOf(0f)
    }
    var contentBoxHeight by remember {
        mutableFloatStateOf(0f)
    }
    var labelHeight by remember {
        mutableFloatStateOf(0f)
    }
    val transformedText = remember(value, visualTransformation) {
        visualTransformation.filter(AnnotatedString(value))
    }.text.text

    val isFocused = interactionSource.collectIsFocusedAsState().value
    val inputState = when {
        isFocused -> InputPhase.Focused
        transformedText.isEmpty() -> InputPhase.UnfocusedEmpty
        else -> InputPhase.UnfocusedNotEmpty
    }
    val padding = Modifier.padding(
        start = 16.dp,
        end = if (trailingIcon != null) 8.dp else 16.dp,
        top = 8.dp,
        bottom = 8.dp,
    )
    val transition = updateTransition(inputState, label = "TextFieldInputState")
    val labelProgress = transition.animateFloat(label = "labelProgress") {
        when (it) {
            InputPhase.Focused -> 1f
            InputPhase.UnfocusedEmpty -> 0f
            InputPhase.UnfocusedNotEmpty -> 1f
        }
    }
    val labelPadding = transition.animateDp(label = "labelPadding") {
        when (it) {
            InputPhase.Focused -> 0.dp
            InputPhase.UnfocusedEmpty -> ((contentBoxHeight - labelHeight) / 2).toDp()
            InputPhase.UnfocusedNotEmpty -> 0.dp
        }
    }
    val placeholderOpacity by transition.animateFloat(label = "PlaceholderOpacity",
        transitionSpec = {
            if (InputPhase.Focused isTransitioningTo InputPhase.UnfocusedEmpty) {
                tween(
                    durationMillis = 67, easing = LinearEasing
                )
            } else if (InputPhase.UnfocusedEmpty isTransitioningTo InputPhase.Focused || InputPhase.UnfocusedNotEmpty isTransitioningTo InputPhase.UnfocusedEmpty) {
                tween(
                    durationMillis = 83, delayMillis = 67, easing = LinearEasing
                )
            } else {
                spring()
            }
        }) {
        when (it) {
            InputPhase.Focused -> if (value.isEmpty()) 1f else 0f
            InputPhase.UnfocusedEmpty -> if (label != null) 0f else 1f
            InputPhase.UnfocusedNotEmpty -> 0f
        }
    }
    //Some case of placeholderColor is handle by placeholderOpacity
    val placeholderColor by transition.animateColor(label = "placeholderColor") {
        when (it) {
            InputPhase.Focused -> Colors.Gray40
            InputPhase.UnfocusedEmpty -> Colors.Gray50
            InputPhase.UnfocusedNotEmpty -> Color.Transparent
        }
    }
    val decoratedLabel: @Composable (() -> Unit)? = label?.let {
        @Composable {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        lerp(
                            TextStyles.Body2.copy(
                                color = Colors.Gray50,
                            ), TextStyles.Caption3.copy(
                                color = Colors.Gray50,
                            ), labelProgress.value
                        ).toSpanStyle(),
                    ) {
                        append(it)
                    }
                    if (isRequired) withStyle(
                        lerp(
                            TextStyles.Body2.copy(
                                color = Colors.Red40,
                            ), TextStyles.Caption3.copy(
                                color = Colors.Red40,
                            ), labelProgress.value
                        ).toSpanStyle(),
                    ) { append("*") }

                },
                modifier = Modifier.padding(top = labelPadding.value),
                onTextLayout = {
                    labelHeight = it.size.height.toFloat()
                    if (it.lineCount > 1) {
                        extraLabelSize =
                            it.getBoundingBox(0).height * (it.lineCount - 1)
                    } else {
                        extraLabelSize = 0F
                    }

                }
            )
        }
    }
    val decoratedLeading: @Composable (RowScope.() -> Unit)? = leadingIcon?.let { icon ->
        @Composable {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 12.dp, end = 16.dp)
                    .size(16.dp),
                tint = Colors.Gray50
            )
        }
    }

    val decoratedHintText: @Composable (() -> Unit)? = placeholder?.let {
        @Composable {
            Text(
                text = it,
                style = TextStyles.Body2.copy(
                    color = placeholderColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier
                    .padding(top = if (label != null) 22.dp else 12.dp)
                    .alpha(placeholderOpacity)
            )
        }
    }

    val decoratedError: @Composable (() -> Unit)? = errorText?.let {
        @Composable {
            AnimatedVisibility(visible = isError, modifier = Modifier) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = it,
                        style = TextStyles.Body4.copy(
                            color = Colors.Red60,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                    )
                }
            }
        }
    }

    val decoratedHelperText: @Composable (() -> Unit)? = helperText?.let {
        @Composable {
            Box(
                modifier = Modifier
                    .padding(top = if (isError) 0.dp else 4.dp)
            ) {
                Text(
                    text = it, style = TextStyles.Body4.copy(
                        color = Colors.Gray50,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ), modifier = Modifier
                )
            }
        }
    }

    val decoratedTrailingIcon: @Composable (RowScope.() -> Unit)? = trailingIcon?.let {
        @Composable {
            Icon(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = enabled) {
                        onTrailingIconClick?.invoke()
                    }
                    .size(40.dp)
                    .padding(8.dp),
                tint = Colors.Gray50
            )
        }
    }

    Column {
        Row(
            modifier = Modifier
                .background(
                    when {
                        !enabled -> colors.disabledContainerColor
                        isError -> colors.errorContainerColor
                        isFocused -> colors.focusedContainerColor
                        else -> colors.unfocusedContainerColor
                    }, shape = shape
                )
                .heightIn(min = minHeight)
                .border(
                    BorderStroke(
                        1.dp, SolidColor(
                            when {
                                !enabled -> colors.disabledIndicatorColor
                                isError -> colors.errorIndicatorColor
                                isFocused -> colors.focusedIndicatorColor
                                else -> colors.unfocusedIndicatorColor
                            }
                        )
                    ), shape
                )
                .then(padding)
                .fillMaxHeight()
                .onSizeChanged { size ->
                    contentBoxHeight = size.height.toFloat()
                },
        ) {
            if (decoratedLeading != null) {
                decoratedLeading()
            }
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (decoratedLabel != null) {
                    decoratedLabel()
                }
                if (decoratedHintText != null) {
                    decoratedHintText()
                }
                Box(
                    modifier = Modifier
                        .padding(
                            top = extraLabelSize.toDp() + if (label != null) 22.dp else 12.dp,
                            bottom = if (label != null) 0.dp else 8.dp
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
            if (decoratedTrailingIcon != null) {
                decoratedTrailingIcon()
            }
        }
        if (decoratedError != null) {
            decoratedError()
        }
        if (decoratedHelperText != null) {
            decoratedHelperText()
        }
    }
}

enum class InputPhase {
    Focused,
    UnfocusedEmpty,
    UnfocusedNotEmpty
}

@Composable
fun Float.toDp(): Dp {
    return with(LocalDensity.current) { this@toDp.toDp() }
}


@Composable
fun TSMaterialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    supportingText: String? = null,
    label: String?
) {
    val bgColor = if (enabled) if (isError) Colors.Red10 else Colors.White else Colors.Gray15
    val colors = TextFieldDefaults.colors(
        disabledIndicatorColor = Colors.Gray15,
        focusedIndicatorColor = Colors.Primary,
        unfocusedIndicatorColor = Colors.Gray30,
        unfocusedContainerColor = bgColor,
        unfocusedLabelColor = Colors.Gray50,
        focusedLabelColor = Colors.Gray50,
        disabledLabelColor = Colors.Gray50,
        cursorColor = Colors.Primary,
        errorIndicatorColor = Colors.Red50,
        errorCursorColor = Colors.Red50,
    )
    val mergedTextStyle = TextStyles.Body2.copy(
        fontWeight = FontWeight.W500,
        color = Colors.Gray90,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    TextField(
        value = value,
        onValueChange = onValueChange,
        colors = colors,
        textStyle = mergedTextStyle,
        interactionSource = interactionSource,
        supportingText = supportingText?.let {
            @Composable {
                Text(text = it)
            }
        },
        isError = isError,
        enabled = enabled,
        label = label?.let {
            @Composable {
                Text(text = it)
            }
        },
    )
}