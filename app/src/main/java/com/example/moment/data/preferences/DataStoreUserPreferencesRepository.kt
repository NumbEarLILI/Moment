package com.example.moment.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {
    private val dataStore = context.userPreferencesDataStore

    override val preferences: Flow<UserAppPreferences> = dataStore.data.map { prefs ->
        val themeRaw = prefs[Keys.THEME_MODE].orEmpty()
        val themeMode = AppThemeMode.entries.find { it.name == themeRaw } ?: AppThemeMode.SYSTEM
        UserAppPreferences(
            themeMode = themeMode,
            aiBaseUrl = prefs[Keys.AI_BASE_URL].orEmpty(),
            aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
            aiModel = prefs[Keys.AI_MODEL].orEmpty()
        )
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setAiSettings(baseUrl: String, apiKey: String, model: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AI_BASE_URL] = baseUrl.trim()
            prefs[Keys.AI_API_KEY] = apiKey.trim()
            prefs[Keys.AI_MODEL] = model.trim()
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
    }
}
