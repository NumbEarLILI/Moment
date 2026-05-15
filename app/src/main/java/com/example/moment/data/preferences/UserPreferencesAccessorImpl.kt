package com.example.moment.data.preferences

import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.preferences.UserPreferencesAccessor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class UserPreferencesAccessorImpl @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : UserPreferencesAccessor {
    override suspend fun current(): UserAppPreferences =
        userPreferencesRepository.preferences.first()
}
