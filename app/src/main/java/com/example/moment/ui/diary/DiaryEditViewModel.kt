package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.usecase.SaveDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiaryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diaryRepository: DiaryRepository,
    private val saveDiary: SaveDiaryUseCase
) : ViewModel() {
    private val diaryId: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    private val _uiState = MutableStateFlow(DiaryEditorUiState(date = LocalDate.EPOCH))
    val uiState: StateFlow<DiaryEditorUiState> = _uiState

    init {
        viewModelScope.launch { loadEntry() }
    }

    fun reloadEntry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            loadEntry()
        }
    }

    private suspend fun loadEntry() {
        val entry = diaryRepository.getDiaryById(diaryId)
        if (entry == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "没有找到这篇手帐") }
            return
        }
        applyEntry(entry)
    }

    private fun applyEntry(entry: DiaryEntry) {
        _uiState.update {
            it.copy(
                isLoading = false,
                date = entry.date,
                title = entry.title,
                body = entry.body,
                highlights = entry.highlights,
                moodSummary = entry.moodSummary,
                sourceFragmentIds = entry.sourceFragmentIds,
                imageUris = entry.imageUris,
                locationPins = entry.locationPins
            )
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        if (state.sourceFragmentIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "缺少关联碎片记录，无法保存。请从日历重新生成手帐。") }
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
                    sourceFragmentIds = state.sourceFragmentIds,
                    imageUris = state.imageUris,
                    locationPins = state.locationPins
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存失败，请稍后重试") }
            }
        }
    }
}
