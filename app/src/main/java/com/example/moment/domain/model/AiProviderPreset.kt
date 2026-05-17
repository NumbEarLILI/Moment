package com.example.moment.domain.model

/**
 * 设置页「一键填入」的 OpenAI 兼容接口预设（API Key 仍须用户自行填写并点保存）。
 *
 * URL 须含 `/v1` 等路径前缀，以便 [com.example.moment.data.llm.OpenAiChatUrls] 正确拼接 `/chat/completions`。
 */
data class AiProviderPreset(
    val id: String,
    val displayLabel: String,
    val baseUrl: String,
    val defaultModel: String,
) {
    fun matchesForm(baseUrl: String, model: String): Boolean {
        val a = baseUrl.trim().trimEnd('/').lowercase()
        val b = this.baseUrl.trim().trimEnd('/').lowercase()
        return a == b && model.trim() == defaultModel.trim()
    }
}

object AiProviderPresets {
    val entries: List<AiProviderPreset> = listOf(
        AiProviderPreset(
            id = "deepseek",
            displayLabel = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
        ),
        AiProviderPreset(
            id = "dashscope",
            displayLabel = "阿里百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-vl-plus",
        ),
        AiProviderPreset(
            id = "openai",
            displayLabel = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
        ),
    )
}
