package com.example.moment.data.nas

import com.example.moment.domain.model.UserAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasImageUploadModeTest {

    @Test
    fun defaultPreferences_uploadCompressedImagesToNas() {
        val prefs = UserAppPreferences()

        val mode = NasImageUploadMode.fromPreferences(prefs)

        assertFalse(prefs.uploadOriginalImagesToNas)
        assertFalse(mode.uploadOriginal)
        assertEquals("0.jpg", mode.remoteFileName(0))
        assertEquals("images/0.jpg", mode.relativeImagePath(0))
        assertEquals("image/jpeg", mode.contentType(null))
    }

    @Test
    fun originalUploadPreference_usesImageFileExtensionAndMime() {
        val prefs = UserAppPreferences(uploadOriginalImagesToNas = true)

        val mode = NasImageUploadMode.fromPreferences(prefs)

        assertTrue(mode.uploadOriginal)
        assertEquals("7.png", mode.remoteFileName(7, ".png"))
        assertEquals("images/7.png", mode.relativeImagePath(7, ".png"))
        assertEquals("image/png", mode.contentType("image/png"))
        assertEquals("application/octet-stream", mode.contentType(null))
    }

    @Test
    fun originalImageExtension_prefersMimeTypeThenUriExtension() {
        assertEquals(".jpg", nasOriginalImageExtension("image/jpeg", "content://x/no-name"))
        assertEquals(".webp", nasOriginalImageExtension("image/webp", "content://x/no-name"))
        assertEquals(".heic", nasOriginalImageExtension(null, "content://x/photo.HEIC"))
        assertEquals(".jpg", nasOriginalImageExtension(null, "content://x/no-name"))
        assertEquals(".heif", nasImageExtensionFromPath("images/0.heif"))
        assertEquals(".webp", nasImageExtensionFromPath("images/0.webp"))
    }
}
