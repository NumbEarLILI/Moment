package com.example.moment.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FragmentAiStory(
    val fragmentId: Long,
    val text: String
)
