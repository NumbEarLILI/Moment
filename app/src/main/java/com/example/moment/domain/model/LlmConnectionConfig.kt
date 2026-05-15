package com.example.moment.domain.model

data class LlmConnectionConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

fun UserAppPreferences.toLlmConnectionConfig(): LlmConnectionConfig? =
    if (!isAiDiaryConfigured()) {
        null
    } else {
        LlmConnectionConfig(
            baseUrl = aiBaseUrl.trim(),
            apiKey = aiApiKey.trim(),
            model = aiModel.trim()
        )
    }
