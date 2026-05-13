package com.example.moment.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.Mood
import com.example.moment.domain.usecase.AddFragmentResult
import com.example.moment.domain.usecase.AddFragmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val addFragment: AddFragmentUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState

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
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                addFragment(
                    content = state.content,
                    imageUris = state.imageUris.csvValues(),
                    mood = state.mood,
                    tags = state.tags.csvValues()
                )
            }.onSuccess { result ->
                when (result) {
                    AddFragmentResult.Empty -> _uiState.update {
                        it.copy(isSaving = false, errorMessage = "至少写一句话或添加一张图片")
                    }
                    is AddFragmentResult.Saved -> _uiState.update { it.copy(isSaving = false, saved = true) }
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存碎片失败，请稍后重试") }
            }
        }
    }

    private fun String.csvValues(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class CaptureUiState(
    val content: String = "",
    val tags: String = "",
    val imageUris: String = "",
    val mood: Mood? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)
