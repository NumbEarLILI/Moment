package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleLinkAttachPolicyTest {

    @Test
    fun `the device that already opened a client gatt must not also take the server role`() {
        assertTrue(
            !BleLinkAttachPolicy.shouldAcceptAsServer(
                outgoingClient = true
            )
        )
    }

    @Test
    fun `the peripheral accepts the incoming connection as server`() {
        assertTrue(
            BleLinkAttachPolicy.shouldAcceptAsServer(
                outgoingClient = false
            )
        )
    }

    @Test
    fun `an 8kb jpeg still fits in one chat frame`() {
        val jpeg = ByteArray(NearbyAvatarPolicy.MAX_BYTES) { 7 }
        val frame = NearbyChatFrame.FragmentShare(
            messageId = "m-f1",
            senderId = "node-a",
            senderName = "阿七",
            sentAtEpochMillis = 9L,
            ttl = 8,
            card = SharedFragmentCard(
                stableId = "sid",
                content = "出门散步",
                createdAtEpochMillis = 1L
            ),
            jpeg = jpeg
        )
        val encoded = NearbyChatWire.encode(frame)
        val decoded = NearbyChatWire.decode(encoded) as NearbyChatFrame.FragmentShare

        assertTrue(encoded.length < NearbyChatWire.MAX_FRAME_CHARS)
        assertEquals(jpeg.size, decoded.jpeg.size)
        assertTrue(jpeg.contentEquals(decoded.jpeg))
    }
}
