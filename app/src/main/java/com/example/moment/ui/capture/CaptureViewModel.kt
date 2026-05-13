package com.example.moment.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.Mood
import com.example.moment.domain.usecase.AddFragmentResult
import com.example.moment.domain.usecase.AddFragmentUseCase
import com.example.moment.domain.usecase.GetFragmentByIdUseCase
import com.example.moment.domain.usecase.UpdateFragmentResult
import com.example.moment.domain.usecase.UpdateFragmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val addFragment: AddFragmentUseCase,
    private val updateFragment: UpdateFragmentUseCase,
    private val getFragmentById: GetFragmentByIdUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState

    init {
        val id = savedStateHandle.get<Long>(ARG_FRAGMENT_ID) ?: 0L
        if (id > 0) {
            _uiState.update { it.copy(editingFragmentId = id, isLoadingDraft = true) }
            viewModelScope.launch {
                runCatching { getFragmentById(id) }
                    .onSuccess { fragment ->
                        if (fragment != null) {
                            _uiState.update {
                                it.copy(
                                    isLoadingDraft = false,
                                    content = fragment.content,
                                    tags = fragment.tags.joinToString(", "),
                                    imageUris = fragment.imageUris.joinToString(", "),
                                    mood = fragment.mood,
                                    errorMessage = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoadingDraft = false,
                                    errorMessage = "找不到这条碎片，可能已被删除。"
                                )
                            }
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                isLoadingDraft = false,
                                errorMessage = "加载碎片失败，请稍后重试"
                            )
                        }
                    }
            }
        }
    }

    fun updateContent(value: String) = _uiState.update { it.copy(content = value, errorMessage = null) }
    fun updateTags(value: String) = _uiState.update { it.copy(tags = value) }
    fun updateImageUris(value: String) = _uiState.update { it.copy(imageUris = value) }
    fun updateMood(value: Mood?) = _uiState.update { it.copy(mood = value) }
    fun addImageUris(values: List<String>) = _uiState.update {
        val merged = (it.imageUris.csvValues() + values).distinct().joinToString(", ")
        it.copy(imageUris = merged, errorMessage = null)
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoadingDraft) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                if (state.editingFragmentId > 0) {
                    when (
                        updateFragment(
                            id = state.editingFragmentId,
                            content = state.content,
                            imageUris = state.imageUris.csvValues(),
                            mood = state.mood,
                            tags = state.tags.csvValues()
                        )
                    ) {
                        UpdateFragmentResult.Empty -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "至少写一句话或添加一张图片")
                        }
                        UpdateFragmentResult.NotFound -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "找不到这条碎片，可能已被删除。")
                        }
                        UpdateFragmentResult.Saved -> _uiState.update { it.copy(isSaving = false, saved = true) }
                    }
                } else {
                    when (
                        addFragment(
                            content = state.content,
                            imageUris = state.imageUris.csvValues(),
                            mood = state.mood,
                            tags = state.tags.csvValues()
                        )
                    ) {
                        AddFragmentResult.Empty -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "至少写一句话或添加一张图片")
                        }
                        is AddFragmentResult.Saved -> _uiState.update { it.copy(isSaving = false, saved = true) }
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存碎片失败，请稍后重试") }
            }
        }
    }

    private fun String.csvValues(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val ARG_FRAGMENT_ID = "fragmentId"
    }
}

data class CaptureUiState(
    val editingFragmentId: Long = 0L,
    val isLoadingDraft: Boolean = false,
    val content: String = "",
    val tags: String = "",
    val imageUris: String = "",
    val mood: Mood? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)
