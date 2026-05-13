package com.example.moment.ui.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.location.NominatimReverseGeocoder
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.usecase.RefreshSavedDiaryFromFragmentsUseCase
import com.example.moment.domain.usecase.UpdateFragmentLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlacePickViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nominatim: NominatimReverseGeocoder,
    private val updateFragmentLocation: UpdateFragmentLocationUseCase,
    private val refreshSavedDiaryFromFragments: RefreshSavedDiaryFromFragmentsUseCase
) : ViewModel() {

    private val initialLat: Double = savedStateHandle.get<String>(ARG_LAT)!!.toDouble()
    private val initialLng: Double = savedStateHandle.get<String>(ARG_LNG)!!.toDouble()
    private val initialHint: String = savedStateHandle.get<String>(ARG_HINT).orEmpty()
    private val fragmentId: Long = savedStateHandle.get<String>(ARG_FRAGMENT_ID)?.toLongOrNull() ?: 0L
    private val diaryId: Long = savedStateHandle.get<String>(ARG_DIARY_ID)?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(
        PlacePickUiState(
            placeName = initialHint,
            mapLat = initialLat,
            mapLng = initialLng
        )
    )
    val uiState: StateFlow<PlacePickUiState> = _uiState.asStateFlow()

    fun updatePlaceName(value: String) = _uiState.update { it.copy(placeName = value, errorMessage = null) }

    fun onMapPosition(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(mapLat = latitude, mapLng = longitude, errorMessage = null) }
        viewModelScope.launch {
            val name = _uiState.value.placeName.trim()
            if (name.isNotEmpty()) return@launch
            val suggested = nominatim.reverseLabel(latitude, longitude)
            if (!suggested.isNullOrBlank()) {
                _uiState.update { s -> s.copy(placeName = suggested) }
            }
        }
    }

    fun confirm() {
        val s = _uiState.value
        val name = s.placeName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请填写地点名称") }
            return
        }
        val location = FragmentLocation(latitude = s.mapLat, longitude = s.mapLng, label = name)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                when {
                    fragmentId > 0L -> {
                        require(updateFragmentLocation(fragmentId, location)) {
                            "找不到对应碎片"
                        }
                        if (diaryId > 0L) {
                            refreshSavedDiaryFromFragments(diaryId)
                        }
                    }
                    else -> Unit
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, finishedLocation = location) }
            }.onFailure {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "保存失败，请稍后重试")
                }
            }
        }
    }

    fun consumeFinish() = _uiState.update { it.copy(finishedLocation = null) }

    companion object {
        const val ARG_LAT = "lat"
        const val ARG_LNG = "lng"
        const val ARG_HINT = "hint"
        const val ARG_FRAGMENT_ID = "fragmentId"
        const val ARG_DIARY_ID = "diaryId"
    }
}

data class PlacePickUiState(
    val placeName: String = "",
    val mapLat: Double = 0.0,
    val mapLng: Double = 0.0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val finishedLocation: FragmentLocation? = null
)
