package tss.t.sharedlibrary.ui.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object TSShapes {
    val rounded4 by lazy { RoundedCornerShape(4.dp) }
    val rounded8 by lazy { RoundedCornerShape(8.dp) }
    val rounded10 by lazy { RoundedCornerShape(10.dp) }
    val rounded12 by lazy { RoundedCornerShape(12.dp) }
    val rounded16 by lazy { RoundedCornerShape(16.dp) }
    val rounded20 by lazy { RoundedCornerShape(20.dp) }

    val roundedTop4 by lazy { RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp) }
    val roundedTop8 by lazy { RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp) }
    val roundedTop10 by lazy { RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) }
    val roundedTop12 by lazy { RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) }
    val roundedTop16 by lazy { RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) }
    val roundedTop20 by lazy { RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) }
}