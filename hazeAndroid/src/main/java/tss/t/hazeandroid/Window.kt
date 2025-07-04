// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package tss.t.hazeandroid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import kotlin.concurrent.getOrSet

internal fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  val loc = tmpArray.getOrSet { IntArray(2) }
  currentValueOf(LocalView).rootView.getLocationOnScreen(loc)
  return Offset(loc[0].toFloat(), loc[1].toFloat())
}

private val tmpArray = ThreadLocal<IntArray>()
