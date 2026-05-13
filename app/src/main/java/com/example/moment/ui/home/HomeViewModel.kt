package com.example.moment.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveFragmentsForDateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeFragmentsForDate: ObserveFragmentsForDateUseCase,
    private val deleteFragment: DeleteFragmentUseCase
) : ViewModel() {
    val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<HomeUiState> = observeFragmentsForDate(today)
        .map<List<LifeFragment>, HomeUiState> { HomeUiState(fragments = it, isLoading = false) }
        .catch { emit(HomeUiState(isLoading = false, errorMessage = "读取记录失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteFragment(id)
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val fragments: List<LifeFragment> = emptyList(),
    val errorMessage: String? = null
)
