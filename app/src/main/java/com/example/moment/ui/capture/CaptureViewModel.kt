package com.example.moment.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.location.FragmentLocationCapture
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.usecase.AddFragmentResult
import com.example.moment.domain.usecase.AddFragmentUseCase
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.GetFragmentByIdUseCase
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import com.example.moment.domain.usecase.ObserveFragmentsForDateUseCase
import com.example.moment.domain.usecase.SuggestMomentCaptionFromImagesUseCase
import com.example.moment.domain.time.resolveNewFragmentRecordedAt
import com.example.moment.domain.usecase.UpdateFragmentResult
import com.example.moment.domain.usecase.UpdateFragmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val addFragment: AddFragmentUseCase,
    private val updateFragment: UpdateFragmentUseCase,
    private val deleteFragment: DeleteFragmentUseCase,
    private val getFragmentById: GetFragmentByIdUseCase,
    private val suggestCaptionFromImages: SuggestMomentCaptionFromImagesUseCase,
    observeFragmentsForDate: ObserveFragmentsForDateUseCase,
    observeDiaryEntries: ObserveDiaryEntriesUseCase,
    private val fragmentLocationCapture: FragmentLocationCapture,
    savedStateHandle: SavedStateHandle,
    private val zoneId: ZoneId,
    private val clock: Clock
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    private val imageAutoSuggestMutex = Mutex()

    private val newFragmentForDate: LocalDate? =
        savedStateHandle.get<String>(ARG_FOR_DATE)?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

    private val contextDay = MutableStateFlow<LocalDate?>(
        if ((savedStateHandle.get<Long>(ARG_FRAGMENT_ID) ?: 0L) > 0L) {
            null
        } else {
            newFragmentForDate ?: clock.instant().atZone(zoneId).toLocalDate()
        }
    )

    val uiState: StateFlow<CaptureUiState> = combine(
        contextDay,
        _uiState,
        observeDiaryEntries()
    ) { dayNullable, state, diaryEntries ->
        Triple(dayNullable, state, diaryEntries)
    }
        // 必须用最新的 _uiState.value 合并碎片列表：仅 observeFragmentsForDate 发射时 combine 不会触发，
        // 若沿用 flatMapLatest 闭包里旧的 state，会回滚用户正在输入的正文并造成光标异常。
        .flatMapLatest { (dayNullable, _, diaryEntries) ->
            if (dayNullable == null) {
                flowOf(
                    _uiState.value.copy(
                        summaryCalendarDay = null,
                        otherFragmentsOnDay = emptyList(),
                        canGenerateDiary = false,
                        savedDiaryEntries = diaryEntries
                    )
                )
            } else {
                observeFragmentsForDate(dayNullable).map { fragments ->
                    val base = _uiState.value
                    base.copy(
                        summaryCalendarDay = dayNullable,
                        otherFragmentsOnDay = if (base.editingFragmentId > 0) {
                            fragments.filter { it.id != base.editingFragmentId }
                        } else {
                            fragments
                        },
                        canGenerateDiary = fragments.isNotEmpty(),
                        savedDiaryEntries = diaryEntries
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    init {
        val id = savedStateHandle.get<Long>(ARG_FRAGMENT_ID) ?: 0L
        if (id > 0) {
            _uiState.update { it.copy(editingFragmentId = id, isLoadingDraft = true) }
            viewModelScope.launch {
                runCatching { getFragmentById(id) }
                    .onSuccess { fragment ->
                        if (fragment != null) {
                            contextDay.value = fragment.createdAt.atZone(zoneId).toLocalDate()
                            _uiState.update {
                                it.copy(
                                    isLoadingDraft = false,
                                    content = fragment.content,
                                    tags = fragment.tags.joinToString(", "),
                                    imageUris = fragment.imageUris.joinToString(", "),
                                    mood = fragment.mood,
                                    baselineLocation = fragment.location,
                                    locationOverride = null,
                                    errorMessage = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoadingDraft = false,
                                    errorMessage = "找不到这条碎片，可能已被删除。"
                                )
                            }
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                isLoadingDraft = false,
                                errorMessage = "加载碎片失败，请稍后重试"
                            )
                        }
                    }
            }
        }
    }

    fun updateContent(value: String) = _uiState.update { it.copy(content = value, errorMessage = null) }
    fun updateTags(value: String) = _uiState.update { it.copy(tags = value) }
    fun updateImageUris(value: String) = _uiState.update { it.copy(imageUris = value) }

    fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        _uiState.update { state ->
            val list = state.tags.csvValues().toMutableList()
            if (list.any { it.equals(tag, ignoreCase = true) }) return@update state
            list.add(tag)
            state.copy(tags = list.joinToString(", "))
        }
    }

    fun removeTag(tag: String) = _uiState.update { state ->
        val remaining = state.tags.csvValues().filterNot { it == tag }
        state.copy(tags = remaining.joinToString(", "))
    }

    fun addImageUris(values: List<String>) {
        _uiState.update {
            val merged = (it.imageUris.csvValues() + values).distinct().joinToString(", ")
            it.copy(imageUris = merged, errorMessage = null)
        }
        viewModelScope.launch {
            imageAutoSuggestMutex.withLock {
                val uris = _uiState.value.imageUris.csvValues()
                if (uris.isNotEmpty()) {
                    runAutoSuggestFromImages(uris)
                }
            }
        }
    }

    fun removeImageUri(uri: String) = _uiState.update { state ->
        val target = uri.trim()
        val remaining = state.imageUris.csvValues().filterNot { it.trim() == target }
        state.copy(imageUris = remaining.joinToString(", "))
    }

    fun applyPickedLocationFromJson(json: String) {
        runCatching {
            Json.decodeFromString(FragmentLocation.serializer(), json)
        }.onSuccess { loc ->
            _uiState.update { it.copy(locationOverride = loc, errorMessage = null) }
        }
    }

    fun requestPlacePickSeed(onReady: (Double, Double, String) -> Unit) {
        viewModelScope.launch {
            val s = _uiState.value
            val seed = s.locationOverride ?: s.baselineLocation
                ?: runCatching { fragmentLocationCapture.captureIfPermitted() }.getOrNull()
            if (seed == null) {
                _uiState.update {
                    it.copy(errorMessage = "暂时无法获取位置，请检查定位权限或稍后再试")
                }
            } else {
                onReady(seed.latitude, seed.longitude, seed.label.orEmpty())
            }
        }
    }

    private suspend fun runAutoSuggestFromImages(uris: List<String>) {
        _uiState.update { it.copy(isAnalyzingImages = true, errorMessage = null) }
        runCatching { suggestCaptionFromImages(uris, null) }
            .onSuccess { suggestion ->
                _uiState.update { state ->
                    val newContent = when {
                        state.content.isBlank() -> suggestion.suggestedContent
                        else -> state.content.trimEnd() + "\n\n" + suggestion.suggestedContent
                    }
                    val mergedTags =
                        (state.tags.csvValues() + suggestion.suggestedTags)
                            .map { t -> t.trim() }
                            .filter { t -> t.isNotEmpty() }
                            .distinct()
                    state.copy(
                        content = newContent,
                        tags = mergedTags.joinToString(", "),
                        mood = state.mood,
                        isAnalyzingImages = false,
                        errorMessage = null
                    )
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(
                        isAnalyzingImages = false,
                        errorMessage = "识别图片失败，请检查权限或稍后重试"
                    )
                }
            }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoadingDraft || state.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                if (state.editingFragmentId > 0) {
                    when (
                        updateFragment(
                            id = state.editingFragmentId,
                            content = state.content,
                            imageUris = state.imageUris.csvValues(),
                            mood = state.mood,
                            tags = state.tags.csvValues(),
                            location = state.locationOverride ?: state.baselineLocation
                        )
                    ) {
                        UpdateFragmentResult.Empty -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "至少写一句话或添加一张图片")
                        }
                        UpdateFragmentResult.NotFound -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "找不到这条碎片，可能已被删除。")
                        }
                        UpdateFragmentResult.Saved -> _uiState.update { it.copy(isSaving = false, saved = true) }
                    }
                } else {
                    val recordedAt = resolveNewFragmentRecordedAt(clock, zoneId, newFragmentForDate)
                    val location = state.locationOverride
                        ?: runCatching { fragmentLocationCapture.captureIfPermitted() }.getOrNull()
                    when (
                        addFragment(
                            content = state.content,
                            imageUris = state.imageUris.csvValues(),
                            mood = state.mood,
                            tags = state.tags.csvValues(),
                            recordedAt = recordedAt,
                            location = location
                        )
                    ) {
                        AddFragmentResult.Empty -> _uiState.update {
                            it.copy(isSaving = false, errorMessage = "至少写一句话或添加一张图片")
                        }
                        is AddFragmentResult.Saved -> _uiState.update { it.copy(isSaving = false, saved = true) }
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存碎片失败，请稍后重试") }
            }
        }
    }

    fun deleteEditingFragment() {
        val id = _uiState.value.editingFragmentId
        if (id <= 0L || _uiState.value.isLoadingDraft || _uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            runCatching { deleteFragment(id) }
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false, saved = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isDeleting = false, errorMessage = "删除失败，请稍后重试")
                    }
                }
        }
    }

    private fun String.csvValues(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val ARG_FRAGMENT_ID = "fragmentId"
        const val ARG_FOR_DATE = "forDate"
    }
}

data class CaptureUiState(
    val editingFragmentId: Long = 0L,
    val isLoadingDraft: Boolean = false,
    val content: String = "",
    val tags: String = "",
    val imageUris: String = "",
    val mood: Mood? = null,
    val baselineLocation: FragmentLocation? = null,
    val locationOverride: FragmentLocation? = null,
    val isAnalyzingImages: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    val summaryCalendarDay: LocalDate? = null,
    val otherFragmentsOnDay: List<LifeFragment> = emptyList(),
    val canGenerateDiary: Boolean = false,
    val savedDiaryEntries: List<DiaryEntry> = emptyList()
)
