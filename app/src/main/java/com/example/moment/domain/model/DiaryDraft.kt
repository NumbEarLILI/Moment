package com.example.moment.domain.model

data class DiaryDraft(
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long> = emptyList(),
    val imageUris: List<String> = emptyList()
)
