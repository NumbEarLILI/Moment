package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
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
    private val fragmentRepository: FragmentRepository,
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

    private suspend fun applyEntry(entry: DiaryEntry) {
        if (entry.sourceFragmentStableIds.isNotEmpty()) {
            fragmentRepository.ensureGhostPlaceholderFragmentsForDiary(entry, emptyMap())
        }
        val plog = if (entry.sourceFragmentStableIds.isNotEmpty()) {
            val loaded = fragmentRepository.getFragmentsForStableIds(entry.sourceFragmentStableIds)
            lifeFragmentsForPlogTimeline(
                entry.sourceFragmentStableIds,
                loaded,
                fallbackDiaryDate = entry.date,
            )
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                date = entry.date,
                title = entry.title,
                body = entry.body,
                highlights = entry.highlights,
                moodSummary = entry.moodSummary,
                sourceFragmentStableIds = entry.sourceFragmentStableIds,
                plogFragments = plog,
                fragmentStories = entry.fragmentStories,
                fragmentImageUris = entry.fragmentImageUris,
                imageUris = entry.imageUris,
                locationPins = entry.locationPins
            )
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        if (state.sourceFragmentStableIds.isEmpty()) {
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
                    sourceFragmentStableIds = state.sourceFragmentStableIds,
                    imageUris = state.imageUris,
                    locationPins = state.locationPins,
                    fragmentStories = state.fragmentStories,
                    fragmentImageUris = state.fragmentImageUris
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存失败，请稍后重试") }
            }
        }
    }
}
