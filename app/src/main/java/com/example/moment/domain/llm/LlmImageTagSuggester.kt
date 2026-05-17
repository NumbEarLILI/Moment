package com.example.moment.domain.llm

/**
 * 通过已配置的 OpenAI 兼容多模态接口，根据当前图片 URI 建议中文短标签。
 */
fun interface LlmImageTagSuggester {
    suspend fun suggestTagsFromImageUris(imageUris: List<String>): Result<List<String>>
}
