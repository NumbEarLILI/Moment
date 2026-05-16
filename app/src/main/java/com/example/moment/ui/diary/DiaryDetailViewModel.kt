package com.example.moment.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DeleteDiaryUseCase
import com.example.moment.domain.usecase.ObserveDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiaryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDiary: ObserveDiaryUseCase,
    private val deleteDiary: DeleteDiaryUseCase,
    private val fragmentRepository: FragmentRepository
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle["id"])
    private val _uiState = MutableStateFlow(DiaryDetailUiState())
    val uiState: StateFlow<DiaryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeDiary(id)
                .catch {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "读取日记失败") }
                }
                .collect { entry ->
        val plog = if (entry != null && entry.sourceFragmentStableIds.isNotEmpty()) {
            val loaded = fragmentRepository.getFragmentsForStableIds(entry.sourceFragmentStableIds)
            lifeFragmentsForPlogTimeline(entry.sourceFragmentStableIds, loaded)
        } else {
            emptyList()
        }
                    _uiState.update { prev ->
                        prev.copy(
                            entry = entry,
                            plogFragments = plog,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun requestDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            runCatching { deleteDiary(id) }
                .onSuccess {
                    _uiState.update { it.copy(showDeleteConfirm = false, deleted = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(showDeleteConfirm = false, errorMessage = "删除失败，请稍后重试")
                    }
                }
        }
    }
}

data class DiaryDetailUiState(
    val isLoading: Boolean = true,
    val entry: DiaryEntry? = null,
    val plogFragments: List<LifeFragment> = emptyList(),
    val errorMessage: String? = null,
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false
)
