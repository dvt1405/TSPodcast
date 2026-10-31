package tss.t.sharedlibrary.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Liquid Glass" look for Android (parity with the iOS 26 glass of M5): translucent surfaces
 * with a specular highlight on top, a light-catching gradient rim and a soft floating shadow.
 * Blur comes from Haze where a surface sits over scrolling content (bars); elsewhere the
 * surface is translucent over an already blurred backdrop (Player artwork).
 *
 * The user can turn it off in Settings; [LocalLiquidGlass] carries that choice and every
 * component falls back to its previous solid style.
 */
val LocalLiquidGlass = staticCompositionLocalOf { true }

object GlassTokens {
    /** Fill of a light glass surface over light content. */
    val LightFill = Color.White.copy(alpha = 0.55f)

    /** Fill of a glass button over a dark or colourful backdrop (Player). */
    val ClearFill = Color.White.copy(alpha = 0.18f)

    val Rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.45f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    val Highlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.38f),
        0.45f to Color.White.copy(alpha = 0.06f),
        1f to Color.Transparent,
    )

    val ShadowColor = Color(0xFF0B1A4A)
}

/** The specular sheen on the upper part of a glass surface, drawn over its content's background. */
fun Modifier.glassHighlight(): Modifier = drawWithContent {
    drawContent()
    drawRect(brush = GlassTokens.Highlight, size = size.copy(height = size.height * 0.6f))
}

/** Light-catching rim. */
fun Modifier.glassRim(shape: Shape, width: Dp = 1.dp): Modifier =
    border(width = width, brush = GlassTokens.Rim, shape = shape)

/**
 * A complete glass surface without blur: soft shadow, translucent [fill], sheen and rim.
 * Use over a backdrop that is already blurred or colourful (Player, artwork headers).
 */
fun Modifier.glassSurface(
    shape: Shape,
    fill: Color = GlassTokens.LightFill,
    elevation: Dp = 8.dp,
): Modifier = this
    .shadow(elevation, shape, ambientColor = GlassTokens.ShadowColor, spotColor = GlassTokens.ShadowColor)
    .clip(shape)
    .background(fill)
    .glassHighlight()
    .glassRim(shape)
