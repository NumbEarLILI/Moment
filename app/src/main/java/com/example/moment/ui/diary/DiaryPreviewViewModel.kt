package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
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
    private val saveDiary: SaveDiaryUseCase,
    private val fragmentRepository: FragmentRepository
) : ViewModel() {
    private val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle["date"]))
    private val _uiState = MutableStateFlow(DiaryEditorUiState(date = date))
    val uiState: StateFlow<DiaryEditorUiState> = _uiState

    init {
        viewModelScope.launch { loadDraft() }
    }

    fun reloadDraft() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            loadDraft()
        }
    }

    private suspend fun loadDraft() {
        try {
            val draft = generateDiaryDraft(date)
            val plog = loadPlogFragments(draft)
            applyDraft(draft, plog)
        } catch (e: Throwable) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            val msg = detail?.let { "生成日记失败：$it" } ?: "生成日记失败，请稍后重试"
            _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
        }
    }

    private suspend fun loadPlogFragments(draft: DiaryDraft): List<LifeFragment> {
        if (draft.sourceFragmentIds.isEmpty()) return emptyList()
        val loaded = fragmentRepository.getFragmentsForSourceIds(draft.sourceFragmentIds)
        return lifeFragmentsForPlogTimeline(draft.sourceFragmentIds, loaded)
    }

    private fun applyDraft(draft: DiaryDraft, plogFragments: List<LifeFragment>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                title = draft.title,
                body = draft.body,
                highlights = draft.highlights,
                moodSummary = draft.moodSummary,
                sourceFragmentIds = draft.sourceFragmentIds,
                plogFragments = plogFragments,
                fragmentStories = draft.fragmentStories,
                imageUris = draft.imageUris,
                locationPins = draft.locationPins
            )
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
                    sourceFragmentIds = state.sourceFragmentIds,
                    imageUris = state.imageUris,
                    locationPins = state.locationPins,
                    fragmentStories = state.fragmentStories
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存日记失败，请稍后重试") }
            }
        }
    }
}
