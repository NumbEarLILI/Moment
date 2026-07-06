package com.example.moment.ui.theme

import androidx.compose.ui.graphics.Color
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
}
