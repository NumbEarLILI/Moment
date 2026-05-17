package com.example.moment.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmImageTagsParserTest {

    @Test
    fun parse_plainJson() {
        val r = LlmImageTagsParser.parse("""{"tags":["咖啡","海边"]}""")
        assertEquals(listOf("咖啡", "海边"), r.getOrThrow())
    }

    @Test
    fun parse_fencedMarkdown() {
        val r = LlmImageTagsParser.parse(
            """
            Here:
            ```json
            {"tags":["A","B"]}
            ```
            """.trimIndent()
        )
        assertEquals(listOf("A", "B"), r.getOrThrow())
    }
}
