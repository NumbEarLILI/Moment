package com.example.moment.data.llm

import kotlinx.serialization.json.Json

internal object AiDiaryResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(modelOutput: String): Result<AiDiaryLlmJson> = runCatching {
        val stripped = stripMarkdownFence(modelOutput.trim())
        val objectSlice = extractJsonObject(stripped)
            ?: throw IllegalArgumentException("未在模型输出中找到 JSON 对象")
        json.decodeFromString(AiDiaryLlmJson.serializer(), objectSlice)
    }

    private fun stripMarkdownFence(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            t = t.trim()
            if (t.endsWith("```")) {
                t = t.removeSuffix("```").trim()
            }
        }
        return t
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
