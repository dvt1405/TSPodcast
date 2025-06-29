// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package tss.t.hazeandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.lruCache
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT


import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

class HazeNode(
  var state: HazeState,
  var defaultStyle: HazeStyle,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  DrawModifierNode {

  fun update() {
    state.contentArea.style = { defaultStyle }
  }

  override fun onAttach() {
    update()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    Snapshot.withMutableSnapshot {
      state.contentArea.apply {
        size = coordinates.size.toSize()
        positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
      }
    }
  }

  /**
   * We manually invalidate when things have changed
   */
  override val shouldAutoInvalidate: Boolean = false

  override fun ContentDrawScope.draw() {
    log(TAG) { "start draw()" }

    if (!USE_GRAPHICS_LAYERS) {
      // If we're not using graphics layers, just call drawContent and return early
      drawContent()
      return
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)

    val contentLayer = state.contentLayer
      ?.takeUnless { it.isReleased }
      ?: graphicsContext.createGraphicsLayer().also { state.contentLayer = it }

    // First we draw the composable content into a graphics layer
    contentLayer.record {
      this@draw.drawContent()
    }

    // Now we draw `content` into the window canvas
    drawLayer(contentLayer)

    val tick = Snapshot.withoutReadObservation { state.invalidateTick }
    state.invalidateTick = tick + 1

    log(TAG) { "end draw()" }
  }

  override fun onDetach() {
    super.onDetach()

    state.contentLayer?.let { layer ->
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
    }
    state.contentLayer = null
  }

  private companion object {
    const val TAG = "HazeNode"
  }
}


internal fun HazeEffectNode.createRenderEffect(
  effect: HazeEffect,
  density: Density,
): RenderEffect? =
  with(effect) {
    val blurRadiusPx = with(density) { blurRadiusOrZero.toPx() }
    if (Build.VERSION.SDK_INT >= 31 && blurRadiusPx >= 0.005f) {
      val bounds = Rect(effect.layerOffset, effect.size)
      return AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
        .withNoise(noiseFactor = noiseFactor)
        .withTints(effect.tints, bounds)
        .withMask(effect.mask, bounds)
        .asComposeRenderEffect()
    }
    return null
  }

internal val USE_GRAPHICS_LAYERS: Boolean = Build.VERSION.SDK_INT >= 32

internal fun HazeEffectNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer?,
) = with(drawScope) {
  if (graphicsLayer != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
    drawLayer(graphicsLayer)
  } else {
    val mask = effect.mask
    val tint = effect.fallbackTint

    println("Drawing effect with scrim: tint=$tint, mask=$mask, alpha=${effect.alpha}")

    fun scrim() {
      if (tint is HazeTint.Color) {
        if (mask != null) {
          drawRect(brush = mask, colorFilter = ColorFilter.tint(tint.color))
        } else {
          drawRect(color = tint.color, blendMode = tint.blendMode)
        }
      } else if (tint is HazeTint.Brush) {
        // We don't currently support mask + brush tints
        drawRect(brush = tint.brush, blendMode = tint.blendMode)
      }
    }

    if (effect.alpha != 1f) {
      val paint = Paint().apply {
        this.alpha = effect.alpha
      }
      drawContext.canvas.withSaveLayer(size.toRect(), paint) {
        scrim()
      }
    } else {
      scrim()
    }
  }
}

private val noiseTextureCache = lruCache<Int, Bitmap>(3)

context(CompositionLocalConsumerModifierNode)
private fun getNoiseTexture(noiseFactor: Float): Bitmap {
  val cacheKey = (noiseFactor * 255).roundToInt()
  val cached = noiseTextureCache[cacheKey]
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  // We draw the noise with the given opacity
  val resources = currentValueOf(LocalContext).resources
  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise)
    .withAlpha(noiseFactor)
    .also { noiseTextureCache.put(cacheKey, it) }
}

context(CompositionLocalConsumerModifierNode)
@RequiresApi(31)
private fun AndroidRenderEffect.withNoise(noiseFactor: Float): AndroidRenderEffect = when {
  noiseFactor >= 0.005f -> {
    val noiseShader = BitmapShader(getNoiseTexture(noiseFactor), REPEAT, REPEAT)
    AndroidRenderEffect.createBlendModeEffect(
      AndroidRenderEffect.createShaderEffect(noiseShader), // dst
      this, // src
      BlendMode.DST_ATOP, // blendMode
    )
  }

  else -> this
}

@RequiresApi(31)
private fun AndroidRenderEffect.withMask(mask: Brush?, bounds: Rect): AndroidRenderEffect {
  return withBrush(mask, bounds, BlendMode.SRC_IN)
}

@RequiresApi(31)
private fun AndroidRenderEffect.withBrush(
  brush: Brush?,
  bounds: Rect,
  blendMode: BlendMode = BlendMode.SRC_OVER,
): AndroidRenderEffect {
  val shader = brush?.toShader(bounds.size) ?: return this

  // We need to offset the shader to the bounds
  val inner = AndroidRenderEffect.createOffsetEffect(
    bounds.left,
    bounds.top,
    AndroidRenderEffect.createShaderEffect(shader),
  )

  return AndroidRenderEffect.createBlendModeEffect(inner, this, blendMode)
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTints(tints: List<HazeTint>, bounds: Rect): AndroidRenderEffect {
  return tints.fold(this) { acc, next ->
    acc.withTint(next, bounds)
  }
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTint(
  tint: HazeTint?,
  bounds: Rect,
): AndroidRenderEffect = when {
  tint is HazeTint.Color && tint.color.alpha >= 0.005f -> {
    AndroidRenderEffect.createColorFilterEffect(
      BlendModeColorFilter(tint.color.toArgb(), tint.blendMode.toAndroidBlendMode()),
      this,
    )
  }

  tint is HazeTint.Brush -> withBrush(tint.brush, bounds, tint.blendMode.toAndroidBlendMode())

  else -> this
}

/**
 * Returns a copy of the current [Bitmap], drawn with the given [alpha] value.
 *
 * There might be a better way to do this via a [BlendMode], but none of the results looked as
 * good.
 */
private fun Bitmap.withAlpha(alpha: Float): Bitmap {
  val paint = android.graphics.Paint().apply {
    this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
  }

  return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
    android.graphics.Canvas(it).apply {
      drawBitmap(this@withAlpha, 0f, 0f, paint)
    }
  }
}
