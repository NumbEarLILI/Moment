package com.example.moment.data.llm

import kotlinx.serialization.Serializable

@Serializable
data class AiDiaryLlmJson(
    val title: String,
    val body: String,
    val highlights: List<String> = emptyList(),
    val moodSummary: String? = null
)
