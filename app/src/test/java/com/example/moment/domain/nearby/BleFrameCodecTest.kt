package com.example.moment.domain.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleFrameCodecTest {

    @Test
    fun `a small payload fits in a single end chunk`() {
        val chunks = BleFrameCodec.split("hello".toByteArray(), maxChunkSize = 20)

        assertEquals(1, chunks.size)
        assertEquals(BleFrameCodec.FLAG_END, chunks.single()[0].toInt())
        val assembled = BleFrameCodec.Assembler().push(chunks.single())
        assertEquals("hello", assembled?.toString(Charsets.UTF_8))
    }

    @Test
    fun `a larger payload is split and reassembled in order`() {
        val payload = "字".repeat(50).toByteArray(Charsets.UTF_8)
        val chunks = BleFrameCodec.split(payload, maxChunkSize = 8)

        assertTrue(chunks.size > 1)
        assertEquals(0, chunks.first()[0].toInt() and BleFrameCodec.FLAG_END)
        assertEquals(BleFrameCodec.FLAG_END, chunks.last()[0].toInt() and BleFrameCodec.FLAG_END)

        val assembler = BleFrameCodec.Assembler()
        val completed = chunks.mapNotNull { assembler.push(it) }
        assertEquals(1, completed.size)
        assertArrayEquals(payload, completed.single())
    }

    @Test
    fun `two frames back to back do not leak into each other`() {
        val assembler = BleFrameCodec.Assembler()
        val first = BleFrameCodec.split("one".toByteArray(), maxChunkSize = 8)
        val second = BleFrameCodec.split("two".toByteArray(), maxChunkSize = 8)

        val got = (first + second).mapNotNull { assembler.push(it) }.map { it.toString(Charsets.UTF_8) }

        assertEquals(listOf("one", "two"), got)
    }

    @Test
    fun `empty payload is still a complete frame`() {
        val chunks = BleFrameCodec.split(ByteArray(0))
        assertEquals(1, chunks.size)
        assertArrayEquals(ByteArray(0), BleFrameCodec.Assembler().push(chunks.single()))
    }

    @Test
    fun `ignores a chunk that is only a header with no end flag`() {
        assertNull(BleFrameCodec.Assembler().push(byteArrayOf(0)))
    }

    @Test
    fun `round-trips a chat frame through the BLE chunks`() {
        val frame = NearbyChatFrame.Message(
            messageId = "m-1",
            senderId = "node-a",
            senderName = "阿七",
            body = "第一行\n第二行",
            sentAtEpochMillis = 1L,
            ttl = 8
        )
        val payload = NearbyChatWire.encode(frame).toByteArray(Charsets.UTF_8)
        val assembler = BleFrameCodec.Assembler()
        val rebuilt = BleFrameCodec.split(payload, maxChunkSize = 12)
            .mapNotNull { assembler.push(it) }
            .single()
            .toString(Charsets.UTF_8)

        assertEquals(frame, NearbyChatWire.decode(rebuilt))
    }
}

class BleConnectPolicyTest {

    @Test
    fun `the lexicographically larger node id initiates`() {
        assertTrue(
            BleConnectPolicy.shouldInitiate(
                selfNodeId = "b",
                peerNodeId = "a",
                canAdvertise = true
            )
        )
        assertTrue(
            !BleConnectPolicy.shouldInitiate(
                selfNodeId = "a",
                peerNodeId = "b",
                canAdvertise = true,
                waitingMs = 0L
            )
        )
    }

    @Test
    fun `never connects to itself`() {
        assertTrue(
            !BleConnectPolicy.shouldInitiate(
                selfNodeId = "same",
                peerNodeId = "same",
                canAdvertise = false
            )
        )
    }

    @Test
    fun `a node that cannot advertise always initiates`() {
        assertTrue(
            BleConnectPolicy.shouldInitiate(
                selfNodeId = "a",
                peerNodeId = "b",
                canAdvertise = false
            )
        )
    }

    @Test
    fun `the smaller id initiates after waiting long enough`() {
        assertTrue(
            !BleConnectPolicy.shouldInitiate(
                selfNodeId = "a",
                peerNodeId = "z",
                canAdvertise = true,
                waitingMs = BleConnectPolicy.FALLBACK_AFTER_MS - 1
            )
        )
        assertTrue(
            BleConnectPolicy.shouldInitiate(
                selfNodeId = "a",
                peerNodeId = "z",
                canAdvertise = true,
                waitingMs = BleConnectPolicy.FALLBACK_AFTER_MS
            )
        )
    }

    @Test
    fun `rejects blank ids and a full table`() {
        assertTrue(
            !BleConnectPolicy.shouldInitiate(selfNodeId = "", peerNodeId = "a", canAdvertise = true)
        )
        assertTrue(BleConnectPolicy.canAccept(3, maxNeighbors = 4))
        assertTrue(!BleConnectPolicy.canAccept(4, maxNeighbors = 4))
    }
}
