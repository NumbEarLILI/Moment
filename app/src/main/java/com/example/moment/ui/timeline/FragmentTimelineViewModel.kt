package com.example.moment.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveAllFragmentsUseCase
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FragmentTimelineViewModel @Inject constructor(
    observeAllFragments: ObserveAllFragmentsUseCase,
    observeDiaryEntries: ObserveDiaryEntriesUseCase,
    private val deleteFragment: DeleteFragmentUseCase,
    private val zoneId: ZoneId
) : ViewModel() {
    private val deleteState = MutableStateFlow(FragmentTimelineDeleteState())

    val uiState: StateFlow<FragmentTimelineUiState> = combine(
        combine(observeAllFragments(), observeDiaryEntries()) { fragments, diaries ->
            FragmentTimelineUiState(
                fragments = fragments,
                items = buildTimelineItems(fragments, diaries, zoneId),
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

    private fun buildTimelineItems(
        fragments: List<LifeFragment>,
        diaries: List<DiaryEntry>,
        zoneId: ZoneId
    ): List<FragmentTimelineItem> {
        val fragmentDates = fragments
            .map { LocalDate.ofInstant(it.createdAt, zoneId) }
            .toSet()
        val fragmentItems = fragments.map { FragmentTimelineItem.Fragment(it) }
        val diaryItems = diaries
            .filter { diary -> diary.date !in fragmentDates && diary.hasTimelineContent() }
            .map { FragmentTimelineItem.DiaryFallback(it) }
        return (fragmentItems + diaryItems).sortedByDescending { item ->
            item.sortInstant(zoneId)
        }
    }

    private fun DiaryEntry.hasTimelineContent(): Boolean =
        title.isNotBlank() ||
            body.isNotBlank() ||
            highlights.any { it.isNotBlank() } ||
            !moodSummary.isNullOrBlank() ||
            imageUris.any { it.isNotBlank() }

    private fun FragmentTimelineItem.sortInstant(zoneId: ZoneId): Instant =
        when (this) {
            is FragmentTimelineItem.Fragment -> fragment.createdAt
            is FragmentTimelineItem.DiaryFallback -> diary.date.plusDays(1).atStartOfDay(zoneId).toInstant()
        }
}

sealed interface FragmentTimelineItem {
    data class Fragment(val fragment: LifeFragment) : FragmentTimelineItem
    data class DiaryFallback(val diary: DiaryEntry) : FragmentTimelineItem
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
