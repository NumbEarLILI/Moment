package com.example.moment.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import com.example.moment.domain.usecase.ObserveFragmentsForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeFragmentsForDate: ObserveFragmentsForDateUseCase,
    private val deleteFragment: DeleteFragmentUseCase,
    observeDiaryEntries: ObserveDiaryEntriesUseCase
) : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val selectedDate = MutableStateFlow(today)
    private val visibleMonth = MutableStateFlow(YearMonth.from(today))

    val uiState: StateFlow<HistoryUiState> = combine(selectedDate, visibleMonth) { date, month ->
        date to month
    }.flatMapLatest { (date, month) ->
        combine(observeFragmentsForDate(date), observeDiaryEntries()) { fragments, entries ->
            HistoryUiState(
                selectedDate = date,
                visibleMonth = month,
                fragments = fragments,
                datesWithSavedDiary = entries.map { it.date }.toSet(),
                diaryEntries = entries.filter { it.date == date },
                canGenerateDiary = fragments.isNotEmpty(),
                isLoading = false
            )
        }.catch {
            emit(
                HistoryUiState(
                    selectedDate = date,
                    visibleMonth = month,
                    isLoading = false,
                    errorMessage = "读取记录失败"
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteFragment(id)
        }
    }

    fun previousMonth() {
        visibleMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        visibleMonth.update { it.plusMonths(1) }
    }

    fun onCalendarDayClick(date: LocalDate) {
        selectedDate.value = date
        visibleMonth.value = YearMonth.from(date)
    }
}

data class HistoryUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val fragments: List<LifeFragment> = emptyList(),
    val datesWithSavedDiary: Set<LocalDate> = emptySet(),
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val canGenerateDiary: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
