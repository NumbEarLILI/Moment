package com.example.moment.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.moment.domain.model.UserAppPreferences

/** 非空表示当前已设置自定义全屏背景图 URI（持久化读写交 Preferences）。 */
val LocalAppWallpaperUri = staticCompositionLocalOf<String?> { null }

/** 自定义背景图上方内容区遮罩不透明度；无背景图时忽略。 */
val LocalWallpaperOverlayAlpha = staticCompositionLocalOf {
    UserAppPreferences.DEFAULT_WALLPAPER_OVERLAY_ALPHA
}

@Composable
fun appScaffoldContainerColor(): Color =
    appScaffoldContainerColorForWallpaper(
        background = MaterialTheme.colorScheme.background,
        wallpaperUri = LocalAppWallpaperUri.current,
        overlayAlpha = LocalWallpaperOverlayAlpha.current
    )

fun appScaffoldContainerColorForWallpaper(
    background: Color,
    wallpaperUri: String?,
    overlayAlpha: Float
): Color =
    if (wallpaperUri.isNullOrBlank()) {
        background
    } else {
        background.copy(alpha = overlayAlpha.coerceIn(0f, 1f))
    }

@Composable
fun appRootContainerColor(): Color =
    appRootContainerColorForWallpaper(
        background = MaterialTheme.colorScheme.background,
        wallpaperUri = LocalAppWallpaperUri.current
    )

fun appRootContainerColorForWallpaper(
    background: Color,
    wallpaperUri: String?
): Color =
    if (wallpaperUri.isNullOrBlank()) {
        background
    } else {
        Color.Transparent
    }
