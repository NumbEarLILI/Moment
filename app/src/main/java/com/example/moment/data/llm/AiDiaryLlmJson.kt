package com.example.moment.data.llm

import kotlinx.serialization.Serializable

@Serializable
data class AiFragmentStoryJson(
    val fragmentId: Long,
    val story: String
)

@Serializable
data class AiDiaryLlmJson(
    val title: String,
    val body: String,
    val highlights: List<String> = emptyList(),
    val moodSummary: String? = null,
    val fragmentStories: List<AiFragmentStoryJson> = emptyList()
)
