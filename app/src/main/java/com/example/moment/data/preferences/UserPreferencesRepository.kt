package com.example.moment.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.UserAppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.userPreferencesDataStore

    val preferences: Flow<UserAppPreferences> = dataStore.data.map { prefs ->
        val themeMode = when (val raw = prefs[Keys.THEME_MODE].orEmpty()) {
            "ORIGINAL" -> AppThemeMode.LIGHT
            else -> AppThemeMode.entries.find { it.name == raw } ?: AppThemeMode.SYSTEM
        }
        UserAppPreferences(
            themeMode = themeMode,
            customBackgroundImageUri = prefs[Keys.CUSTOM_BACKGROUND_IMAGE_URI].orEmpty(),
            aiBaseUrl = prefs[Keys.AI_BASE_URL].orEmpty(),
            aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
            aiModel = prefs[Keys.AI_MODEL].orEmpty(),
            nasWebdavBaseUrl = prefs[Keys.NAS_WEBDAV_BASE_URL].orEmpty(),
            nasWebdavUsername = prefs[Keys.NAS_WEBDAV_USERNAME].orEmpty(),
            nasWebdavPassword = prefs[Keys.NAS_WEBDAV_PASSWORD].orEmpty(),
            nasWebdavTrustSelfSignedCertificates = prefs[Keys.NAS_WEBDAV_TRUST_SELF_SIGNED] ?: false
        )
    }

    suspend fun setCustomBackgroundImageUri(uri: String) {
        dataStore.edit { it[Keys.CUSTOM_BACKGROUND_IMAGE_URI] = uri.trim() }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAiSettings(baseUrl: String, apiKey: String, model: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AI_BASE_URL] = baseUrl.trim()
            prefs[Keys.AI_API_KEY] = apiKey.trim()
            prefs[Keys.AI_MODEL] = model.trim()
        }
    }

    suspend fun setNasWebdavSettings(
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSignedCertificates: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.NAS_WEBDAV_BASE_URL] = baseUrl.trim()
            prefs[Keys.NAS_WEBDAV_USERNAME] = username.trim()
            prefs[Keys.NAS_WEBDAV_PASSWORD] = password
            prefs[Keys.NAS_WEBDAV_TRUST_SELF_SIGNED] = trustSelfSignedCertificates
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_BACKGROUND_IMAGE_URI = stringPreferencesKey("custom_background_image_uri")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val NAS_WEBDAV_BASE_URL = stringPreferencesKey("nas_webdav_base_url")
        val NAS_WEBDAV_USERNAME = stringPreferencesKey("nas_webdav_username")
        val NAS_WEBDAV_PASSWORD = stringPreferencesKey("nas_webdav_password")
        val NAS_WEBDAV_TRUST_SELF_SIGNED = booleanPreferencesKey("nas_webdav_trust_self_signed")
    }
}
