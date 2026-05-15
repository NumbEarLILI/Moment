package com.example.moment.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.domain.repository.UserPreferencesRepository
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.UserAppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferences = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAppPreferences()
    )

    private val _aiBaseUrl = MutableStateFlow("")
    private val _aiApiKey = MutableStateFlow("")
    private val _aiModel = MutableStateFlow("")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    fun reloadAiDraftsFromStore() {
        viewModelScope.launch {
            val p = userPreferencesRepository.preferences.first()
            _aiBaseUrl.value = p.aiBaseUrl
            _aiApiKey.value = p.aiApiKey
            _aiModel.value = p.aiModel
        }
    }

    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setAiBaseUrl(value: String) {
        _aiBaseUrl.value = value
    }

    fun setAiApiKey(value: String) {
        _aiApiKey.value = value
    }

    fun setAiModel(value: String) {
        _aiModel.value = value
    }

    fun saveAiSettings() {
        viewModelScope.launch {
            userPreferencesRepository.setAiSettings(
                baseUrl = _aiBaseUrl.value,
                apiKey = _aiApiKey.value,
                model = _aiModel.value
            )
        }
    }
}
