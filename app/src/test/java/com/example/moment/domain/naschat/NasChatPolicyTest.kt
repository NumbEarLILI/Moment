package com.example.moment.domain.naschat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NasChatPathsTest {

    @Test
    fun `conversation folder is stable regardless of argument order`() {
        val a = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val b = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        assertEquals("${a}_$b", NasChatPaths.conversationFolder(a, b))
        assertEquals("${a}_$b", NasChatPaths.conversationFolder(b, a))
    }

    @Test
    fun `peer id is the other side of a folder that includes self`() {
        val a = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val b = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val folder = NasChatPaths.conversationFolder(a, b)
        assertEquals(b, NasChatPaths.peerIdFromFolder(folder, a))
        assertEquals(a, NasChatPaths.peerIdFromFolder(folder, b))
        assertNull(NasChatPaths.peerIdFromFolder(folder, "someone-else"))
    }

    @Test
    fun `chunk names parse back to an index`() {
        assertEquals("chunk-000.jsonl", NasChatPaths.chunkFileName(0))
        assertEquals("chunk-012.jsonl", NasChatPaths.chunkFileName(12))
        assertEquals(0, NasChatPaths.parseChunkIndex("chunk-000.jsonl"))
        assertEquals(12, NasChatPaths.parseChunkIndex("chunk-012.jsonl"))
        assertNull(NasChatPaths.parseChunkIndex("notes.txt"))
    }
}

class NasChatChunkPolicyTest {

    @Test
    fun `empty chunk never rolls so the first message always has a home`() {
        assertTrue(!NasChatChunkPolicy.shouldRollOver(0, NasChatChunkPolicy.MAX_CHUNK_BYTES + 10))
    }

    @Test
    fun `rolls when the next line would pass 1MB`() {
        val almostFull = NasChatChunkPolicy.MAX_CHUNK_BYTES - 8
        assertTrue(!NasChatChunkPolicy.shouldRollOver(almostFull, 8))
        assertTrue(NasChatChunkPolicy.shouldRollOver(almostFull, 9))
    }

    @Test
    fun `next chunk is one after the highest existing index`() {
        assertEquals(0, NasChatChunkPolicy.activeIndex(emptyList()))
        assertEquals(1, NasChatChunkPolicy.nextIndex(emptyList()))
        assertEquals(3, NasChatChunkPolicy.activeIndex(listOf(0, 3, 1)))
        assertEquals(4, NasChatChunkPolicy.nextIndex(listOf(0, 3, 1)))
    }
}

class NasChatJsonlTest {

    @Test
    fun `round-trips messages as one json object per line`() {
        val messages = listOf(
            NasChatWireMessage("m1", "a", "阿七", "你好", 10L),
            NasChatWireMessage("m2", "b", "小明", "在楼下", 11L)
        )
        val file = NasChatJsonl.encodeFile(messages)
        assertTrue(file.lines().filter { it.isNotBlank() }.size == 2)
        assertTrue(!file.contains('\r'))
        assertEquals(messages, NasChatJsonl.parseFile(file))
    }

    @Test
    fun `merge keeps each message id once and sorts by time`() {
        val first = listOf(
            NasChatWireMessage("m2", "b", "小明", "后发", 20L),
            NasChatWireMessage("m1", "a", "阿七", "先发", 10L)
        )
        val second = listOf(
            NasChatWireMessage("m1", "a", "阿七", "先发（重复）", 10L),
            NasChatWireMessage("m3", "a", "阿七", "又一条", 30L)
        )
        val merged = NasChatJsonl.merge(first, second)
        assertEquals(listOf("m1", "m2", "m3"), merged.map { it.messageId })
        assertEquals("先发", merged[0].body)
    }

    @Test
    fun `skips malformed lines instead of failing the chunk`() {
        val raw = """
            {"messageId":"m1","senderId":"a","senderName":"阿七","body":"hi","sentAtEpochMillis":1}
            not-json
            {"messageId":"m2","senderId":"b","senderName":"小明","body":"ok","sentAtEpochMillis":2}
        """.trimIndent()
        assertEquals(listOf("m1", "m2"), NasChatJsonl.parseFile(raw).map { it.messageId })
    }
}
