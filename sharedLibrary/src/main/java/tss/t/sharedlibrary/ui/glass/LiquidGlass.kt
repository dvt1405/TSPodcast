package tss.t.sharedlibrary.ui.glass

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
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
 * Soft floating shadow for a translucent surface, drawn only outside [shape].
 *
 * Do not use `Modifier.shadow(elevation)` under glass: the RenderNode shadow assumes an opaque
 * caster, so Skia tessellates the part under the shape coarsely (a circle becomes a hexagon /
 * octagon) and that polygon shows through the translucent fill. Here the shadow is a blurred
 * copy of the outline with the shape's own area clipped out, so nothing is drawn beneath it.
 */
fun Modifier.glassShadow(
    shape: Shape,
    elevation: Dp = 8.dp,
    color: Color = GlassTokens.ShadowColor,
): Modifier {
    // BlurMaskFilter needs a hardware canvas from API 28; without blur the shadow would be a
    // hard-edged ring, so older devices simply get no shadow (the rim still outlines the surface).
    if (elevation <= 0.dp || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
    return drawWithCache {
        val elevationPx = elevation.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val outlinePath = Path().apply { addOutline(outline) }
        fun blurPaint(alpha: Float, radius: Float): Paint {
            val paint = Paint()
            val framework = paint.asFrameworkPaint()
            framework.isAntiAlias = true
            framework.color = color.copy(alpha = color.alpha * alpha).toArgb()
            framework.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            return paint
        }
        // Same split as a Material elevation shadow: a faint ambient halo all round plus a
        // stronger key-light shadow pushed down.
        val ambient = blurPaint(alpha = 0.10f, radius = elevationPx)
        val spot = blurPaint(alpha = 0.22f, radius = elevationPx * 1.2f)
        val spotOffsetY = elevationPx * 0.5f
        onDrawBehind {
            clipPath(outlinePath, ClipOp.Difference) {
                drawIntoCanvas { canvas ->
                    canvas.drawPath(outlinePath, ambient)
                    canvas.save()
                    canvas.translate(0f, spotOffsetY)
                    canvas.drawPath(outlinePath, spot)
                    canvas.restore()
                }
            }
        }
    }
}

/**
 * A complete glass surface without blur: soft shadow, translucent [fill], sheen and rim.
 * Use over a backdrop that is already blurred or colourful (Player, artwork headers).
 */
fun Modifier.glassSurface(
    shape: Shape,
    fill: Color = GlassTokens.LightFill,
    elevation: Dp = 8.dp,
): Modifier = this
    .glassShadow(shape, elevation)
    .clip(shape)
    .background(fill)
    .glassHighlight()
    .glassRim(shape)
