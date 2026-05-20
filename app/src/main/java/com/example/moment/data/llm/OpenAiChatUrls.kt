package com.example.moment.data.llm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object OpenAiChatUrls {
    fun chatCompletionsEndpoint(baseUrl: String): String {
        val t = baseUrl.trim().trimEnd('/')
        requireSecureOrLocalCleartext(t)
        return if (t.endsWith("/chat/completions", ignoreCase = true)) {
            t
        } else {
            "$t/chat/completions"
        }
    }

    private fun requireSecureOrLocalCleartext(baseUrl: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("无效的模型接口地址")
        if (url.isHttps) return
        val host = url.host.lowercase()
        val local = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        require(local) { "模型接口地址必须使用 HTTPS，或使用本机/模拟器调试地址" }
    }
}
