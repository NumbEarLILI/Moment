package com.example.moment.data.remote.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleDiarySynthesizerTest {

    @Test
    fun chatCompletionsUrlTrimsAndAppendsSegment() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleDiarySynthesizer.chatCompletionsUrl("https://api.openai.com/v1/")
        )
    }

    @Test
    fun chatCompletionsUrlLeavesFullEndpoint() {
        val u = "https://gateway.example/v1/chat/completions"
        assertEquals(u, OpenAiCompatibleDiarySynthesizer.chatCompletionsUrl(u))
    }

    @Test
    fun parseHandbookJsonStripsMarkdownFence() {
        val raw = """
            Here you go:
            ```json
            {"title":"一日","body":"早\\n晚","highlights":["咖啡"],"moodSummary":null}
            ```
        """.trimIndent()
        val parsed = OpenAiCompatibleDiarySynthesizer.parseHandbookJson(raw)
        assertEquals("一日", parsed.title)
        assertEquals("早\n晚", parsed.body)
        assertEquals(listOf("咖啡"), parsed.highlights)
    }

    @Test
    fun parseHandbookJsonReadsMoodSummarySnakeCase() {
        val raw = """{"title":"t","body":"b","highlights":[],"mood_summary":"偏平静"}"""
        val parsed = OpenAiCompatibleDiarySynthesizer.parseHandbookJson(raw)
        assertEquals("偏平静", parsed.resolvedMoodSummary())
    }
}
