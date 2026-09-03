package com.example.moment.domain.nearby

import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyFragmentSharePolicyTest {

    @Test
    fun `bluetooth mesh does not attach fragment photos`() {
        assertTrue(!NearbyFragmentSharePolicy.includeImage(NearbyTransport.Bluetooth))
    }

    @Test
    fun `wifi direct attaches original fragment photos`() {
        assertTrue(NearbyFragmentSharePolicy.includeImage(NearbyTransport.WifiDirect))
    }

    @Test
    fun `nas chat does not attach fragment photos`() {
        assertTrue(!NearbyFragmentSharePolicy.includeImage(NearbyTransport.Nas))
    }

    @Test
    fun `bluetooth shares hide the local preview so sender and peer match`() {
        val jpeg = ByteArray(64) { 1 }
        assertEquals(
            "",
            NearbyFragmentSharePolicy.localPreviewPath(
                transport = NearbyTransport.Bluetooth,
                localPath = "/data/photo.jpg",
                attachedJpeg = jpeg
            )
        )
    }

    @Test
    fun `wifi shares keep the local preview only when a photo was attached`() {
        val jpeg = ByteArray(64) { 1 }
        assertEquals(
            "content://media/1",
            NearbyFragmentSharePolicy.localPreviewPath(
                transport = NearbyTransport.WifiDirect,
                localPath = "content://media/1",
                attachedJpeg = jpeg
            )
        )
        assertEquals(
            "",
            NearbyFragmentSharePolicy.localPreviewPath(
                transport = NearbyTransport.WifiDirect,
                localPath = "content://media/1",
                attachedJpeg = byteArrayOf()
            )
        )
    }

    @Test
    fun `a bluetooth fragment share without jpeg still fits the ble frame budget`() {
        val encoded = NearbyChatWire.encode(sampleShare(jpeg = byteArrayOf()))

        assertTrue(encoded.length < NearbyChatWire.MAX_FRAME_CHARS)
    }

    @Test
    fun `a wifi original photo exceeds the ble budget but fits the wifi budget`() {
        val jpeg = ByteArray(200 * 1024) { 7 }
        val encoded = NearbyChatWire.encode(sampleShare(jpeg = jpeg))

        assertTrue(encoded.length > NearbyChatWire.MAX_FRAME_CHARS)
        assertTrue(encoded.length < NearbyChatWire.WIFI_MAX_FRAME_CHARS)
        val emptyShareChars = NearbyChatWire.encode(sampleShare(jpeg = byteArrayOf())).length
        val maxBase64Chars = 4 * ((NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_BYTES + 2) / 3)
        assertTrue(emptyShareChars + maxBase64Chars < NearbyChatWire.WIFI_MAX_FRAME_CHARS)
    }

    @Test
    fun `wifi frame reader accepts a photo sized payload that ble would reject`() {
        val payload = "x".repeat(NearbyChatWire.MAX_FRAME_CHARS + 80)

        val wifiLine = NearbyChatWire.readFrameLine(
            StringReader("$payload\n"),
            maxChars = NearbyChatWire.WIFI_MAX_FRAME_CHARS
        )

        assertEquals(payload, wifiLine)
    }

    @Test(expected = IOException::class)
    fun `ble frame reader still rejects a photo sized payload`() {
        NearbyChatWire.readFrameLine(
            StringReader("x".repeat(NearbyChatWire.MAX_FRAME_CHARS + 80) + "\n")
        )
    }

    private fun sampleShare(jpeg: ByteArray) = NearbyChatFrame.FragmentShare(
        messageId = "m-f1",
        senderId = "node-a",
        senderName = "阿七",
        sentAtEpochMillis = 9L,
        ttl = 8,
        card = SharedFragmentCard(
            stableId = "sid",
            content = "出门散步",
            mood = "平静",
            tags = listOf("散步"),
            place = "上海",
            weather = "晴  26°",
            createdAtEpochMillis = 1L
        ),
        jpeg = jpeg
    )
}
