package com.example.moment.ui.diary

import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate

/** 手帐预览与编辑界面共用的编辑区状态。 */
data class DiaryEditorUiState(
    val date: LocalDate,
    val isLoading: Boolean = true,
    val title: String = "",
    val body: String = "",
    val highlights: List<String> = emptyList(),
    val moodSummary: String? = null,
    val sourceFragmentStableIds: List<String> = emptyList(),
    val fragmentStories: List<FragmentAiStory> = emptyList(),
    val plogFragments: List<LifeFragment> = emptyList(),
    val fragmentImageUris: Map<String, List<String>> = emptyMap(),
    val imageUris: List<String> = emptyList(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)
