package com.example.moment.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveAllFragmentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FragmentTimelineViewModel @Inject constructor(
    observeAllFragments: ObserveAllFragmentsUseCase,
    private val deleteFragment: DeleteFragmentUseCase
) : ViewModel() {
    private val deleteState = MutableStateFlow(FragmentTimelineDeleteState())

    val uiState: StateFlow<FragmentTimelineUiState> = combine(
        observeAllFragments()
            .map { fragments ->
                FragmentTimelineUiState(
                    fragments = fragments,
                    items = fragments
                        .sortedByDescending { it.createdAt }
                        .map { FragmentTimelineItem.Fragment(it) },
                    isLoading = false
                )
            }
            .catch {
                emit(
                    FragmentTimelineUiState(
                        isLoading = false,
                        errorMessage = "读取碎片时间线失败"
                    )
                )
            },
        deleteState
    ) { state, delete ->
        state.copy(
            deletingFragmentId = delete.deletingFragmentId,
            deleteErrorMessage = delete.deleteErrorMessage
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FragmentTimelineUiState())

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteState.value = FragmentTimelineDeleteState(deletingFragmentId = id)
            runCatching {
                deleteFragment(id)
            }.fold(
                onSuccess = {
                    deleteState.value = FragmentTimelineDeleteState()
                },
                onFailure = {
                    deleteState.value = FragmentTimelineDeleteState(deleteErrorMessage = "删除碎片失败")
                }
            )
        }
    }

    fun clearDeleteError() {
        deleteState.update { it.copy(deleteErrorMessage = null) }
    }
}

sealed interface FragmentTimelineItem {
    val fragment: LifeFragment
    data class Fragment(override val fragment: LifeFragment) : FragmentTimelineItem
}

data class FragmentTimelineUiState(
    val fragments: List<LifeFragment> = emptyList(),
    val items: List<FragmentTimelineItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deletingFragmentId: Long? = null,
    val deleteErrorMessage: String? = null
)

private data class FragmentTimelineDeleteState(
    val deletingFragmentId: Long? = null,
    val deleteErrorMessage: String? = null
)
