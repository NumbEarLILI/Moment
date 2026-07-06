package com.example.moment.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.moment.domain.model.UserAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWallpaperTest {
    @Test
    fun rootContainerIsTransparentWhenWallpaperIsSet() {
        val color = appRootContainerColorForWallpaper(
            background = Color.White,
            wallpaperUri = "content://wallpaper"
        )

        assertEquals(Color.Transparent, color)
    }

    @Test
    fun rootContainerUsesBackgroundWhenWallpaperIsBlank() {
        val color = appRootContainerColorForWallpaper(
            background = Color.White,
            wallpaperUri = ""
        )

        assertEquals(Color.White, color)
    }

    @Test
    fun scaffoldContainerUsesOverlayAlphaWhenWallpaperIsSet() {
        val color = appScaffoldContainerColorForWallpaper(
            background = Color.White,
            wallpaperUri = "content://wallpaper",
            overlayAlpha = 0.5f
        )

        assertEquals(Color.White.copy(alpha = 0.5f), color)
    }

    @Test
    fun scaffoldContainerUsesBackgroundWhenWallpaperIsBlank() {
        val color = appScaffoldContainerColorForWallpaper(
            background = Color.White,
            wallpaperUri = null,
            overlayAlpha = UserAppPreferences.DEFAULT_WALLPAPER_OVERLAY_ALPHA
        )

        assertEquals(Color.White, color)
    }

    @Test
    fun scaffoldContainerClampsOverlayAlpha() {
        val color = appScaffoldContainerColorForWallpaper(
            background = Color.White,
            wallpaperUri = "content://wallpaper",
            overlayAlpha = 1.5f
        )

        assertEquals(Color.White.copy(alpha = 1f), color)
    }
}
