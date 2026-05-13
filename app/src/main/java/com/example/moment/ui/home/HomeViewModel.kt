package com.example.moment.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.GetDiaryForDateUseCase
import com.example.moment.domain.usecase.ObserveFragmentsForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class OpenSavedDiary(val id: Long) : HomeEvent
    data class OpenDiaryPreview(val date: LocalDate) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeFragmentsForDate: ObserveFragmentsForDateUseCase,
    private val deleteFragment: DeleteFragmentUseCase,
    private val getDiaryForDate: GetDiaryForDateUseCase
) : ViewModel() {
    val today: LocalDate = LocalDate.now()

    private val selectedDate = MutableStateFlow(today)
    private val visibleMonth = MutableStateFlow(YearMonth.from(today))

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(selectedDate, visibleMonth) { date, month ->
        date to month
    }.flatMapLatest { (date, month) ->
        observeFragmentsForDate(date).map { fragments ->
            HomeUiState(
                selectedDate = date,
                visibleMonth = month,
                fragments = fragments,
                isLoading = false
            )
        }.catch {
            emit(
                HomeUiState(
                    selectedDate = date,
                    visibleMonth = month,
                    isLoading = false,
                    errorMessage = "读取记录失败"
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
        viewModelScope.launch {
            selectedDate.value = date
            visibleMonth.value = YearMonth.from(date)
            val diary = getDiaryForDate(date)
            if (diary != null) {
                eventChannel.send(HomeEvent.OpenSavedDiary(diary.id))
            } else {
                eventChannel.send(HomeEvent.OpenDiaryPreview(date))
            }
        }
    }
}

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val fragments: List<LifeFragment> = emptyList(),
    val errorMessage: String? = null
)
