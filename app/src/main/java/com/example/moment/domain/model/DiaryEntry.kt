package com.example.moment.domain.model

import java.time.Instant
import java.time.LocalDate

data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long>,
    val imageUris: List<String> = emptyList(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)
