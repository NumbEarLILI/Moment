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
