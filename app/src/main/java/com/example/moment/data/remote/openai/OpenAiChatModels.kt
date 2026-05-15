package com.example.moment.data.remote.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.65
)

@Serializable
internal data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
internal data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto> = emptyList()
)

@Serializable
internal data class ChatChoiceDto(
    val message: ChatMessageDto? = null
)

@Serializable
internal data class AiHandbookJson(
    val title: String,
    val body: String,
    val highlights: List<String> = emptyList(),
    @SerialName("moodSummary")
    val moodSummary: String? = null,
    @SerialName("mood_summary")
    val moodSummarySnake: String? = null
) {
    fun resolvedMoodSummary(): String? = moodSummary?.trim()?.takeIf { it.isNotEmpty() }
        ?: moodSummarySnake?.trim()?.takeIf { it.isNotEmpty() }
}
