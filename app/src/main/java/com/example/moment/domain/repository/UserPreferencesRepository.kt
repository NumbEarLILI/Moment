package com.example.moment.domain.repository

import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.UserAppPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserAppPreferences>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setAiSettings(baseUrl: String, apiKey: String, model: String)
}
