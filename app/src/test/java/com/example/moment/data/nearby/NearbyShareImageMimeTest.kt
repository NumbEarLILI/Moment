package com.example.moment.data.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyShareImageMimeTest {

    @Test
    fun `sniffs jpeg from the soi marker`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        assertEquals("image/jpeg", NearbyShareImageMime.fromBytes(header))
        assertEquals("jpg", NearbyShareImageMime.fileExtension("image/jpeg"))
    }

    @Test
    fun `sniffs png from the signature`() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        assertEquals("image/png", NearbyShareImageMime.fromBytes(header))
        assertEquals("png", NearbyShareImageMime.fileExtension("image/png"))
    }

    @Test
    fun `sniffs webp from the riff header`() {
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
            0x57, 0x45, 0x42, 0x50
        )

        assertEquals("image/webp", NearbyShareImageMime.fromBytes(header))
        assertEquals("webp", NearbyShareImageMime.fileExtension("image/webp"))
    }

    @Test
    fun `unknown bytes default to jpeg so a save still works`() {
        assertEquals("image/jpeg", NearbyShareImageMime.fromBytes(byteArrayOf(1, 2, 3)))
        assertEquals("image/jpeg", NearbyShareImageMime.fromBytes(byteArrayOf()))
    }

    @Test
    fun `legacy storage permission is only required below android 10`() {
        assertTrue(NearbyShareImageMime.needsLegacyWritePermission(28))
        assertTrue(!NearbyShareImageMime.needsLegacyWritePermission(29))
        assertTrue(!NearbyShareImageMime.needsLegacyWritePermission(36))
    }

    @Test
    fun `gallery file names include an extension`() {
        assertEquals("Moment_1700000000000.jpg", NearbyShareImageMime.displayName(1_700_000_000_000L, "image/jpeg"))
        assertEquals("Moment_1700000000000.png", NearbyShareImageMime.displayName(1_700_000_000_000L, "image/png"))
    }
}
