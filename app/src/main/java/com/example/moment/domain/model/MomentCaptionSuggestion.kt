package com.example.moment.domain.model

data class MomentCaptionSuggestion(
    val suggestedContent: String,
    val suggestedTags: List<String>,
    val suggestedMood: Mood?
)
