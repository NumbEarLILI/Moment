package com.example.moment.data.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object LlmImageTagsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(modelOutput: String): Result<List<String>> = runCatching {
        val slice = extractJsonObject(modelOutput.trim())
        val root = json.parseToJsonElement(slice).jsonObject
        val arr = root["tags"]?.jsonArray ?: error("JSON 缺少 tags 数组")
        val tags = arr.mapNotNull { el ->
            val s = (el as? JsonPrimitive)?.content?.trim().orEmpty()
            s.takeIf { it.isNotEmpty() }
        }
        if (tags.isEmpty()) error("tags 为空")
        tags.distinct()
    }

    private fun extractJsonObject(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").trim()
            val fence = t.lastIndexOf("```")
            if (fence >= 0) {
                t = t.substring(0, fence).trim()
            }
        }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        require(start >= 0 && end > start) { "未找到 JSON 对象" }
        return t.substring(start, end + 1)
    }
}
