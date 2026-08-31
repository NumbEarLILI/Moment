package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyFragmentSharePolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyShareImageBytesTest {

    @Test
    fun `passes through an original file that already fits the wifi budget`() {
        val file = File.createTempFile("moment-original", ".jpg")
        try {
            val original = ByteArray(64 * 1024) { 7 }
            file.writeBytes(original)

            val bytes = NearbyShareImageBytes.fromFile(file)

            assertTrue(original.contentEquals(bytes))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing files become an empty attachment`() {
        assertTrue(NearbyShareImageBytes.fromFile(File("/tmp/moment-missing-photo.jpg")).isEmpty())
    }

    @Test
    fun `keeps bytes under the wifi budget without recompressing`() {
        val raw = ByteArray(NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_BYTES) { 3 }

        assertEquals(raw.size, NearbyShareImageBytes.fit(raw).size)
        assertTrue(raw.contentEquals(NearbyShareImageBytes.fit(raw)))
    }
}
