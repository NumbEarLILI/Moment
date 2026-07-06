package com.example.moment.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.UserAppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MineViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val preferences: StateFlow<UserAppPreferences> = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAppPreferences()
    )
}
