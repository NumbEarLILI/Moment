package com.example.moment.domain.model

data class DiaryDraft(
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentStableIds: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val fragmentImageUris: Map<String, List<String>> = emptyMap(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val fragmentStories: List<FragmentAiStory> = emptyList()
)
