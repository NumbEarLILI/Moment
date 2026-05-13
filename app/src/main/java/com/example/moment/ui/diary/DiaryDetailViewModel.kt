package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.usecase.ObserveDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DiaryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDiary: ObserveDiaryUseCase
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle["id"])
    val uiState: StateFlow<DiaryDetailUiState> = observeDiary(id)
        .map { DiaryDetailUiState(entry = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryDetailUiState())
}

data class DiaryDetailUiState(
    val isLoading: Boolean = true,
    val entry: DiaryEntry? = null
)
