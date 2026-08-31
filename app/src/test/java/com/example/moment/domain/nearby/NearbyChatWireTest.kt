package com.example.moment.domain.nearby

import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyChatWireTest {

    @Test
    fun `encodes and decodes a chat message`() {
        val frame = NearbyChatFrame.Message(
            messageId = "m-1",
            senderId = "node-a",
            senderName = "阿七",
            body = "在楼下咖啡店",
            sentAtEpochMillis = 1_700_000_000_000L,
            ttl = 8
        )

        assertEquals(frame, NearbyChatWire.decode(NearbyChatWire.encode(frame)))
    }

    @Test
    fun `encodes and decodes the mesh handshake frames`() {
        val member = MeshMember(
            nodeId = "node-a",
            displayName = "阿七",
            present = true,
            updatedAtEpochMillis = 1L
        )
        val frames = listOf(
            NearbyChatFrame.Hello(member),
            NearbyChatFrame.Presence(member.copy(present = false, updatedAtEpochMillis = 2L)),
            NearbyChatFrame.Roster(listOf(member, member.copy(nodeId = "node-b", displayName = "小明")))
        )

        frames.forEach { frame ->
            assertEquals(frame, NearbyChatWire.decode(NearbyChatWire.encode(frame)))
        }
    }

    @Test
    fun `encodes and decodes an avatar frame`() {
        val frame = NearbyChatFrame.Avatar(
            nodeId = "node-a",
            jpeg = byteArrayOf(1, 2, 3, 4, 5),
            updatedAtEpochMillis = 9L
        )
        val decoded = NearbyChatWire.decode(NearbyChatWire.encode(frame)) as NearbyChatFrame.Avatar

        assertEquals(frame.nodeId, decoded.nodeId)
        assertTrue(frame.jpeg.contentEquals(decoded.jpeg))
        assertEquals(frame.updatedAtEpochMillis, decoded.updatedAtEpochMillis)
    }

    @Test
    fun `keeps a multi-line body on a single wire line`() {
        val frame = NearbyChatFrame.Message(
            messageId = "m-2",
            senderId = "node-a",
            senderName = "阿七",
            body = "第一行\n第二行",
            sentAtEpochMillis = 1L,
            ttl = 8
        )

        val line = NearbyChatWire.encode(frame)

        assertTrue("换行必须被转义，否则一帧会被拆成两帧", !line.contains('\n'))
        assertEquals(frame, NearbyChatWire.decode(line))
    }

    @Test
    fun `decodes unknown and malformed input as null instead of throwing`() {
        assertNull(NearbyChatWire.decode(""))
        assertNull(NearbyChatWire.decode("   "))
        assertNull(NearbyChatWire.decode("not json at all"))
        assertNull(NearbyChatWire.decode("""{"type":"future-frame","x":1}"""))
        assertNull(NearbyChatWire.decode("""{"type":"msg","messageId":"a""""))
    }

    @Test
    fun `sanitize trims and rejects blank messages`() {
        assertEquals("你好", NearbyChatWire.sanitizeMessage("  你好 \n"))
        assertNull(NearbyChatWire.sanitizeMessage("   "))
        assertNull(NearbyChatWire.sanitizeMessage(""))
    }

    @Test
    fun `sanitize truncates overly long messages`() {
        val sanitized = NearbyChatWire.sanitizeMessage("字".repeat(NearbyChatWire.MAX_MESSAGE_CHARS + 50))

        assertEquals(NearbyChatWire.MAX_MESSAGE_CHARS, sanitized?.length)
    }

    @Test
    fun `reads newline delimited frames`() {
        val reader = StringReader("first\nsecond\n")

        assertEquals("first", NearbyChatWire.readFrameLine(reader))
        assertEquals("second", NearbyChatWire.readFrameLine(reader))
        assertNull(NearbyChatWire.readFrameLine(reader))
    }

    @Test
    fun `reads a trailing frame that has no newline`() {
        val reader = StringReader("only")

        assertEquals("only", NearbyChatWire.readFrameLine(reader))
        assertNull(NearbyChatWire.readFrameLine(reader))
    }

    @Test
    fun `ignores carriage returns`() {
        val reader = StringReader("first\r\nsecond\r\n")

        assertEquals("first", NearbyChatWire.readFrameLine(reader))
        assertEquals("second", NearbyChatWire.readFrameLine(reader))
    }

    @Test
    fun `a full roster of members still fits in one frame`() {
        val roster = NearbyChatFrame.Roster(
            (1..9).map {
                MeshMember(
                    nodeId = "node-$it",
                    displayName = "成员$it",
                    present = true,
                    updatedAtEpochMillis = it.toLong()
                )
            }
        )

        val line = NearbyChatWire.encode(roster)

        assertTrue(line.length < NearbyChatWire.MAX_FRAME_CHARS)
        assertEquals(roster, NearbyChatWire.decode(line))
    }

    @Test(expected = IOException::class)
    fun `rejects a frame longer than the limit`() {
        NearbyChatWire.readFrameLine(StringReader("x".repeat(50)), maxChars = 8)
    }
}
