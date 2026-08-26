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

    fun beginAvatarCrop(uri: Uri) {
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            _cropBusy.value = true
            try {
                val preview = withContext(Dispatchers.IO) {
                    cropProcessor.preparePreview {
                        context.contentResolver.openInputStream(uri)
                    }
                }
                _cropSession.value = AvatarCropSession(
                    previewPath = preview.file.absolutePath,
                    imageWidth = preview.width,
                    imageHeight = preview.height
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { cropProcessor.clearPreview() }
                _cropSession.value = null
                _userMessage.emit("无法设置头像")
            } finally {
                _cropBusy.value = false
            }
        }
    }

    fun confirmAvatarCrop(state: AvatarCropState, cropDiameter: Float) {
        val session = _cropSession.value ?: return
        if (cropDiameter <= 0f || session.imageWidth <= 0 || session.imageHeight <= 0) return
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            _cropBusy.value = true
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
                    cropProcessor.clearPreview()
                    saved
                }
                userPreferencesRepository.setAvatarImagePath(file.absolutePath)
                _cropSession.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _userMessage.emit("无法设置头像")
            } finally {
                _cropBusy.value = false
            }
        }
    }

    fun cancelAvatarCrop() {
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { cropProcessor.clearPreview() }
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
        cropProcessor.clearPreview()
        super.onCleared()
    }
}
