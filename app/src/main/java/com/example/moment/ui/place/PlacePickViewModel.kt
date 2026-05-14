package com.example.moment.ui.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.location.AmapReverseGeocoder
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
    private val amapReverseGeocoder: AmapReverseGeocoder,
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
            mapLng = initialLng,
            placeNameUserLocked = false
        )
    )
    val uiState: StateFlow<PlacePickUiState> = _uiState.asStateFlow()

    fun updatePlaceName(value: String) = _uiState.update { s ->
        if (value.isBlank()) {
            s.copy(placeName = "", errorMessage = null, placeNameUserLocked = false)
        } else {
            val userEdited = value != s.placeName
            s.copy(
                placeName = value,
                errorMessage = null,
                placeNameUserLocked = s.placeNameUserLocked || userEdited
            )
        }
    }

    fun reportMapDiagnostic(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { s ->
            val merged = if (s.mapDiagnostics.isBlank()) trimmed else s.mapDiagnostics + "\n" + trimmed
            val clipped =
                if (merged.length > 4000) merged.substring(merged.length - 4000) else merged
            s.copy(mapDiagnostics = clipped)
        }
    }

    /** 非错误类线索（灰字），用于确认 JS 是否执行、canvas 是否出现。 */
    fun reportMapTrace(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { s ->
            val merged = if (s.mapTrace.isBlank()) trimmed else s.mapTrace + "\n" + trimmed
            val clipped = if (merged.length > 2500) merged.substring(merged.length - 2500) else merged
            s.copy(mapTrace = clipped)
        }
    }

    fun onMapPosition(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(mapLat = latitude, mapLng = longitude, errorMessage = null) }
        viewModelScope.launch {
            val locked = _uiState.value.placeNameUserLocked
            val amap = amapReverseGeocoder.reverseGeocode(latitude, longitude)
            amap.failureDetail?.let { reportMapTrace("高德逆地理: $it") }
            val suggested = amap.label ?: nominatim.reverseLabel(latitude, longitude)
            if (!suggested.isNullOrBlank() && !locked) {
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
    /** WebView / 高德 JS 加载阶段的诊断信息（便于排查白名单、Key 类型等）。 */
    val mapDiagnostics: String = "",
    /** 地图脚本生命周期（非错误），用于确认是否执行到某一步。 */
    val mapTrace: String = "",
    /** 用户是否手动改过地点名称；为 true 时移动图钉只更新坐标，不覆盖名称。 */
    val placeNameUserLocked: Boolean = false,
    val finishedLocation: FragmentLocation? = null
)
