package com.example.moment.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeDiaryEntries: ObserveDiaryEntriesUseCase
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = observeDiaryEntries()
        .map { HistoryUiState(entries = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<DiaryEntry> = emptyList()
)
