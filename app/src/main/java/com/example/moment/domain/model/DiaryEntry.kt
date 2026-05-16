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
    /** 手帐保存的「每条碎片对应图片」，用于 plog 卡片与 NAS 仅恢复日记等场景。 */
    val fragmentImageUris: Map<Long, List<String>> = emptyMap(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val fragmentStories: List<FragmentAiStory> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)
