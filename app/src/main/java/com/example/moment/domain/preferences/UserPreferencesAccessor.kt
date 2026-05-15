package com.example.moment.domain.preferences

import com.example.moment.domain.model.UserAppPreferences

interface UserPreferencesAccessor {
    suspend fun current(): UserAppPreferences
}
