package com.example.moment.ui.mine

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.avatar.UserAvatarStore
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.UserAppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val avatarStore: UserAvatarStore
) : ViewModel() {
    val preferences: StateFlow<UserAppPreferences> = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAppPreferences()
    )

    fun setAvatarFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选图片")
                    stream.use { avatarStore.import(it) }
                }
            }.onSuccess { file ->
                userPreferencesRepository.setAvatarImagePath(file.absolutePath)
            }
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { avatarStore.clear() }
            userPreferencesRepository.setAvatarImagePath("")
        }
    }
}
