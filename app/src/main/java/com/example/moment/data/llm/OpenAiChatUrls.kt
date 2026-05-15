package com.example.moment.data.llm

internal object OpenAiChatUrls {
    fun chatCompletionsEndpoint(baseUrl: String): String {
        val t = baseUrl.trim().trimEnd('/')
        return if (t.endsWith("/chat/completions", ignoreCase = true)) {
            t
        } else {
            "$t/chat/completions"
        }
    }
}
