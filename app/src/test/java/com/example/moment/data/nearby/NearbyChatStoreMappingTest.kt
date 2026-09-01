package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyTransport
import java.io.File
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
        assertEquals(
            NearbyTransport.Nas.name,
            message.copy(messageId = "m-3", peerId = "user-b").toEntity(NearbyTransport.Nas).transport
        )
    }

    @Test
    fun `round-trips a nas 1-1 message with peer id`() {
        val message = NearbyChatMessage(
            messageId = "m-nas",
            senderId = "user-a",
            senderName = "阿七",
            text = "在楼下",
            fromMe = true,
            sentAtEpochMillis = 42L,
            peerId = "user-b"
        )

        val restored = message.toEntity(NearbyTransport.Nas).toDomain()

        assertEquals(message, restored)
        assertEquals("user-b", restored.peerId)
    }

    @Test
    fun `round-trips a shared fragment card`() {
        val message = NearbyChatMessage(
            messageId = "f-1",
            senderId = "node-a",
            senderName = "阿七",
            text = "出门散步",
            fromMe = true,
            sentAtEpochMillis = 9L,
            fragment = com.example.moment.domain.nearby.SharedFragmentCard(
                stableId = "sid-3",
                content = "出门散步",
                mood = "平静",
                tags = listOf("散步"),
                place = "上海",
                weather = "晴  26°",
                createdAtEpochMillis = 42L
            ),
            imagePath = "/tmp/share.jpg"
        )

        val restored = message.toEntity(NearbyTransport.Bluetooth).toDomain()

        assertEquals(message, restored)
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

class NearbyShareImageStoreTest {

    @Test
    fun `writes a fragment share thumbnail by message id`() {
        val dir = createTempDir(prefix = "moment-shares")
        try {
            val store = NearbyShareImageStore(dir)
            val saved = store.save("f-1", byteArrayOf(9, 8, 7))

            assertTrue(saved.isFile)
            assertEquals(byteArrayOf(9, 8, 7).toList(), saved.readBytes().toList())
            assertEquals(store.fileFor("f-1").absolutePath, saved.absolutePath)
            store.deleteIfManaged(saved.absolutePath)
            assertTrue(!saved.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `does not delete files outside the share directory`() {
        val dir = createTempDir(prefix = "moment-shares")
        val outsider = File.createTempFile("moment-outside", ".jpg")
        try {
            outsider.writeBytes(byteArrayOf(1))
            NearbyShareImageStore(dir).deleteIfManaged(outsider.absolutePath)
            assertTrue(outsider.isFile)
        } finally {
            dir.deleteRecursively()
            outsider.delete()
        }
    }
}
