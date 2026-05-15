package com.example.moment.data.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.65
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto> = emptyList()
)

@Serializable
data class ChatChoiceDto(
    val message: ChatAssistantMessageDto = ChatAssistantMessageDto()
)

@Serializable
data class ChatAssistantMessageDto(
    val content: String = "",
    @SerialName("refusal")
    val refusal: String? = null
)
