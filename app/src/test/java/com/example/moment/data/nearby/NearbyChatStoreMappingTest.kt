package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyChatStoreMappingTest {

    @Test
    fun `round-trips a message through the room entity`() {
        val message = NearbyChatMessage(
            messageId = "m-1",
            senderId = "node-a",
            senderName = "阿七",
            text = "在楼下",
            fromMe = false,
            sentAtEpochMillis = 42L
        )

        val restored = message.toEntity(NearbyTransport.Bluetooth).toDomain()

        assertEquals(message, restored)
    }

    @Test
    fun `keeps bluetooth and wifi as separate transports`() {
        val message = NearbyChatMessage(
            messageId = "m-1",
            senderId = "node-a",
            senderName = "阿七",
            text = "在楼下",
            fromMe = false,
            sentAtEpochMillis = 42L
        )

        assertEquals(
            NearbyTransport.Bluetooth.name,
            message.toEntity(NearbyTransport.Bluetooth).transport
        )
        assertEquals(
            NearbyTransport.WifiDirect.name,
            message.copy(messageId = "m-2").toEntity(NearbyTransport.WifiDirect).transport
        )
    }
}

class PeerAvatarStoreTest {

    @Test
    fun `writes and reads a peer avatar file`() {
        val dir = createTempDir(prefix = "moment-avatars")
        try {
            val store = PeerAvatarStore(dir)
            val saved = store.save("node-a", byteArrayOf(1, 2, 3, 4))

            assertTrue(saved.isFile)
            assertEquals(saved.absolutePath, store.pathIfPresent("node-a"))
            assertEquals(mapOf("node-a" to saved.absolutePath), store.snapshot())
        } finally {
            dir.deleteRecursively()
        }
    }
}
