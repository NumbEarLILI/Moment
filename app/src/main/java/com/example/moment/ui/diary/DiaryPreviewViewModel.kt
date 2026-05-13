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
            runCatching { generateDiaryDraft(date) }
                .onSuccess { draft ->
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
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "生成日记失败，请稍后重试") }
                }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        if (state.sourceFragmentIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "今天还没有碎片，不能保存空手帐") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                saveDiary(
                    date = state.date,
                    title = state.title,
                    body = state.body,
                    highlights = state.highlights,
                    moodSummary = state.moodSummary,
                    sourceFragmentIds = state.sourceFragmentIds
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存日记失败，请稍后重试") }
            }
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
    val saved: Boolean = false,
    val errorMessage: String? = null
)
