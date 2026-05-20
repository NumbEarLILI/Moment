package com.example.moment.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiChatUrlsTest {

    @Test
    fun allowsHttpsEndpoint() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiChatUrls.chatCompletionsEndpoint("https://api.example.com/v1")
        )
    }

    @Test
    fun allowsCleartextOnlyForEmulatorLocalhostEndpoint() {
        assertEquals(
            "http://10.0.2.2:11434/v1/chat/completions",
            OpenAiChatUrls.chatCompletionsEndpoint("http://10.0.2.2:11434/v1")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCleartextRemoteEndpoint() {
        OpenAiChatUrls.chatCompletionsEndpoint("http://api.example.com/v1")
    }
}
