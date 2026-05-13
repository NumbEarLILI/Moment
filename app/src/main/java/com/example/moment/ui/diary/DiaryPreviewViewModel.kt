package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.usecase.GenerateDiaryDraftUseCase
import com.example.moment.domain.usecase.SaveDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiaryPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val generateDiaryDraft: GenerateDiaryDraftUseCase,
    private val saveDiary: SaveDiaryUseCase
) : ViewModel() {
    private val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle["date"]))
    private val _uiState = MutableStateFlow(DiaryPreviewUiState(date = date))
    val uiState: StateFlow<DiaryPreviewUiState> = _uiState

    init {
        viewModelScope.launch {
            val draft = generateDiaryDraft(date)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = draft.title,
                    body = draft.body,
                    highlights = draft.highlights,
                    moodSummary = draft.moodSummary,
                    sourceFragmentIds = draft.sourceFragmentIds
                )
            }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            saveDiary(
                date = state.date,
                title = state.title,
                body = state.body,
                highlights = state.highlights,
                moodSummary = state.moodSummary,
                sourceFragmentIds = state.sourceFragmentIds
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}

data class DiaryPreviewUiState(
    val date: LocalDate,
    val isLoading: Boolean = true,
    val title: String = "",
    val body: String = "",
    val highlights: List<String> = emptyList(),
    val moodSummary: String? = null,
    val sourceFragmentIds: List<Long> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false
)
