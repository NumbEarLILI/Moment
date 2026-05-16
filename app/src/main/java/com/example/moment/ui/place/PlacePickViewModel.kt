package com.example.moment.ui.place

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.BuildConfig
import com.example.moment.data.location.AmapReverseGeocoder
import com.example.moment.data.location.ChinaCoordinateTransform
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
    private val fragmentStableId: String =
        savedStateHandle.get<String>(ARG_FRAGMENT_ID).orEmpty().trim()
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

    init {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    buildString {
                        append("【配置】逆地理 Web 服务 Key：")
                        append(if (BuildConfig.AMAP_WEB_SERVICE_KEY.isNotBlank()) "已填写" else "未填写")
                        append("；签名私钥：")
                        append(if (BuildConfig.AMAP_WEB_SERVICE_SECRET.isNotBlank()) "已填写" else "未填写")
                    }
                )
            }
            val s = _uiState.value
            onMapPosition(s.mapLat, s.mapLng)
        }
    }

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
        Log.w(TAG, trimmed)
    }

    fun reportMapTrace(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !BuildConfig.DEBUG) return
        Log.d(TAG, trimmed)
    }

    fun onMapPosition(latitude: Double, longitude: Double) {
        if (BuildConfig.DEBUG) Log.d(TAG, "【图钉】Kotlin 收到 lat=$latitude lng=$longitude")
        _uiState.update { it.copy(mapLat = latitude, mapLng = longitude, errorMessage = null) }
        viewModelScope.launch {
            val locked = _uiState.value.placeNameUserLocked
            if (BuildConfig.DEBUG) Log.d(TAG, "【逆地理】请求 lat=$latitude lng=$longitude 名称锁=$locked")
            val amap = amapReverseGeocoder.reverseGeocode(latitude, longitude)
            if (BuildConfig.DEBUG) {
                if (amap.label != null) Log.d(TAG, "【逆地理】高德成功")
                else Log.d(TAG, "【逆地理】高德未返回：${amap.failureDetail ?: "无详情"}")
            }
            val nomin = run {
                val (wgsLat, wgsLng) = ChinaCoordinateTransform.gcj02ToWgs84(latitude, longitude)
                nominatim.reverseLabel(wgsLat, wgsLng)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, if (nomin != null) "【逆地理】OSM 有备选" else "【逆地理】OSM 无结果")
            }
            val suggested = amap.label ?: nomin
            when {
                !suggested.isNullOrBlank() && !locked -> {
                    _uiState.update { s -> s.copy(placeName = suggested) }
                }
                else -> Unit
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
                    fragmentStableId.isNotBlank() && fragmentStableId != "0" -> {
                        require(updateFragmentLocation(fragmentStableId, location)) {
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
        private const val TAG = "MomentPlacePick"
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
    /** 用户是否手动改过地点名称；为 true 时移动图钉只更新坐标，不覆盖名称。 */
    val placeNameUserLocked: Boolean = false,
    val finishedLocation: FragmentLocation? = null
)
