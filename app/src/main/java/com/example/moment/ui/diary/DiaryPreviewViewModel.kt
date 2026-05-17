package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DiaryGenerationMode
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
    private val diaryRepository: DiaryRepository,
    private val generateDiaryDraft: GenerateDiaryDraftUseCase,
    private val saveDiary: SaveDiaryUseCase,
    private val fragmentRepository: FragmentRepository
) : ViewModel() {
    private val diaryIdArg: Long = savedStateHandle.navArgLong("diaryId")
    private val dateArg: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle["date"]))
    private val _uiState = MutableStateFlow(DiaryEditorUiState(date = dateArg))
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
            val explicitAnchor = if (diaryIdArg > 0L) diaryRepository.getDiaryById(diaryIdArg) else null
            val mergeDate = explicitAnchor?.date ?: dateArg
            val anchor = explicitAnchor ?: diaryRepository.getDiaryForDate(mergeDate)
            val draft = generateDiaryDraft(mergeDate, DiaryGenerationMode.AUTO, anchor)
            val plog = loadPlogFragments(draft, anchor)
            applyDraft(draft, plog, mergeDate)
        } catch (e: Throwable) {
            val detail = e.message?.takeIf { it.isNotBlank() }
            val msg = detail?.let { "生成日记失败：$it" } ?: "生成日记失败，请稍后重试"
            _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
        }
    }

    private suspend fun loadPlogFragments(draft: DiaryDraft, anchor: DiaryEntry?): List<LifeFragment> {
        if (draft.sourceFragmentStableIds.isEmpty()) return emptyList()
        val loaded = fragmentRepository.getFragmentsForStableIds(draft.sourceFragmentStableIds)
        return lifeFragmentsForPlogTimeline(
            draft.sourceFragmentStableIds,
            loaded,
            anchor?.fragmentCreatedAtEpochMillis.orEmpty()
        )
    }

    private fun applyDraft(draft: DiaryDraft, plogFragments: List<LifeFragment>, mergeDate: LocalDate) {
        _uiState.update {
            it.copy(
                isLoading = false,
                date = mergeDate,
                title = draft.title,
                body = draft.body,
                highlights = draft.highlights,
                moodSummary = draft.moodSummary,
                sourceFragmentStableIds = draft.sourceFragmentStableIds,
                plogFragments = plogFragments,
                fragmentStories = draft.fragmentStories,
                fragmentImageUris = draft.fragmentImageUris,
                imageUris = draft.imageUris,
                locationPins = draft.locationPins
            )
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        if (state.sourceFragmentStableIds.isEmpty()) {
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
                    sourceFragmentStableIds = state.sourceFragmentStableIds,
                    imageUris = state.imageUris,
                    locationPins = state.locationPins,
                    fragmentStories = state.fragmentStories,
                    fragmentImageUris = state.fragmentImageUris
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存日记失败，请稍后重试") }
            }
        }
    }
}

/** Navigation 有时把数字参数放进 Bundle 为 String；统一转成 Long，避免锚点日记 id 丢失导致合并乱套。 */
private fun SavedStateHandle.navArgLong(key: String): Long {
    val raw = get<Any>(key) ?: return 0L
    return when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
}
