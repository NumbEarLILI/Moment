package com.example.moment.ui.mine

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.avatar.AvatarCropProcessor
import com.example.moment.data.avatar.UserAvatarStore
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.avatar.AvatarCropMath
import com.example.moment.domain.avatar.AvatarCropState
import com.example.moment.domain.model.UserAppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AvatarCropSession(
    val sessionId: Long,
    val previewPath: String,
    val imageWidth: Int,
    val imageHeight: Int
)

@HiltViewModel
class MineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val avatarStore: UserAvatarStore,
    private val cropProcessor: AvatarCropProcessor
) : ViewModel() {
    val preferences: StateFlow<UserAppPreferences> = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserAppPreferences()
    )

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage = _userMessage.asSharedFlow()

    private val _cropSession = MutableStateFlow<AvatarCropSession?>(null)
    val cropSession = _cropSession.asStateFlow()

    private val _cropBusy = MutableStateFlow(false)
    val cropBusy = _cropBusy.asStateFlow()
    private var cropJob: Job? = null
    private var cropGeneration = 0L
    @Volatile private var confirming = false

    fun beginAvatarCrop(uri: Uri) {
        if (confirming) return
        val sessionId = ++cropGeneration
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            _cropBusy.value = true
            try {
                val preview = withContext(Dispatchers.IO) {
                    cropProcessor.preparePreview(sessionId) {
                        context.contentResolver.openInputStream(uri)
                    }
                }
                if (sessionId != cropGeneration) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        cropProcessor.clearPreview(sessionId)
                    }
                    return@launch
                }
                _cropSession.value = AvatarCropSession(
                    sessionId = sessionId,
                    previewPath = preview.file.absolutePath,
                    imageWidth = preview.width,
                    imageHeight = preview.height
                )
            } catch (error: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    cropProcessor.clearPreview(sessionId)
                }
                throw error
            } catch (_: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    cropProcessor.clearPreview(sessionId)
                }
                if (sessionId == cropGeneration) {
                    _cropSession.value = null
                    _userMessage.emit("无法设置头像")
                }
            } finally {
                if (sessionId == cropGeneration && !confirming) {
                    _cropBusy.value = false
                }
            }
        }
    }

    fun confirmAvatarCrop(state: AvatarCropState, cropDiameter: Float) {
        val session = _cropSession.value ?: return
        if (confirming || cropDiameter <= 0f || session.imageWidth <= 0 || session.imageHeight <= 0) return
        confirming = true
        _cropBusy.value = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val rect = AvatarCropMath.pixelRect(
                        state = state,
                        imageWidth = session.imageWidth,
                        imageHeight = session.imageHeight,
                        cropDiameter = cropDiameter
                    )
                    val jpeg = cropProcessor.cropToJpeg(File(session.previewPath), rect)
                    val saved = avatarStore.import(ByteArrayInputStream(jpeg))
                    cropProcessor.clearPreview(session.sessionId)
                    saved
                }
                userPreferencesRepository.setAvatarImagePath(file.absolutePath)
                _cropSession.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _userMessage.emit("无法设置头像")
            } finally {
                confirming = false
                _cropBusy.value = false
            }
        }
    }

    fun cancelAvatarCrop() {
        if (confirming) return
        cropGeneration++
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { cropProcessor.clearAllPreviews() }
            _cropSession.value = null
            _cropBusy.value = false
        }
    }

    fun clearAvatar() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { avatarStore.clear() }
            userPreferencesRepository.setAvatarImagePath("")
        }
    }

    override fun onCleared() {
        cropProcessor.clearAllPreviews()
        super.onCleared()
    }
}
