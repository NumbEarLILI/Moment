package com.example.moment.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDiaryResponseParserTest {

    @Test
    fun parsesPlainJson() {
        val raw = """{"title":"测试","body":"第一段。\n\n第二段。","highlights":["亮点"],"moodSummary":"平静"}"""
        val parsed = AiDiaryResponseParser.parse(raw).getOrThrow()
        assertEquals("测试", parsed.title)
        assertTrue(parsed.body.contains("第一段"))
        assertEquals(listOf("亮点"), parsed.highlights)
        assertEquals("平静", parsed.moodSummary)
        assertTrue(parsed.fragmentStories.isEmpty())
    }

    @Test
    fun parsesFragmentStoriesArray() {
        val raw =
            """{"title":"一日","body":"收束。","highlights":[],"moodSummary":null,"fragmentStories":[{"fragmentId":10,"story":"晨间一则。"},{"fragmentId":11,"story":"午后一则。"}]}"""
        val parsed = AiDiaryResponseParser.parse(raw).getOrThrow()
        assertEquals(2, parsed.fragmentStories.size)
        assertEquals(10L, parsed.fragmentStories[0].fragmentId)
        assertEquals("晨间一则。", parsed.fragmentStories[0].story)
    }

    @Test
    fun stripsMarkdownFence() {
        val raw = """
            ```json
            {"title":"A","body":"B","highlights":[],"moodSummary":null}
            ```
        """.trimIndent()
        val parsed = AiDiaryResponseParser.parse(raw).getOrThrow()
        assertEquals("A", parsed.title)
        assertEquals("B", parsed.body)
    }
}
