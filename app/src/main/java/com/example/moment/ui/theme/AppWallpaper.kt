package com.example.moment.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 非空表示当前已设置自定义全屏背景图 URI（持久化读写交 Preferences）。 */
val LocalAppWallpaperUri = staticCompositionLocalOf<String?> { null }

@Composable
fun appScaffoldContainerColor(): Color {
    val uri = LocalAppWallpaperUri.current
    return if (uri.isNullOrBlank()) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
    }
}
