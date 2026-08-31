package com.example.moment.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * 输入框往往不在窗口最底（底下还有导航栏）。直接 [imePadding] 会按整段键盘高度再垫一次，
 * 键盘和输入框之间就空出那截已经占掉的高度。先把这段吃掉，只补键盘真正盖住的部分。
 */
fun Modifier.positionAwareImePadding(): Modifier = composed {
    val density = LocalDensity.current
    var alreadyAboveWindowBottomPx by remember { mutableIntStateOf(0) }
    onGloballyPositioned { coordinates ->
        val rootHeight = coordinates.findRootCoordinates().size.height
        val composableBottom = coordinates.positionInWindow().y + coordinates.size.height
        alreadyAboveWindowBottomPx = remainingImeConsumePx(rootHeight, composableBottom)
    }
        .consumeWindowInsets(
            PaddingValues(bottom = with(density) { alreadyAboveWindowBottomPx.toDp() })
        )
        .imePadding()
}

internal fun remainingImeConsumePx(windowHeightPx: Int, composableBottomPx: Float): Int =
    (windowHeightPx - composableBottomPx.roundToInt()).coerceAtLeast(0)
