package com.example.moment.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.data.location.FragmentLocationCapture
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.domain.model.NasArchiveConflictInfo
import com.example.moment.domain.model.toNasWebdavConfig
import com.example.moment.domain.repository.NasArchiveRepository
import com.example.moment.domain.time.FragmentRecordedAtText
import com.example.moment.domain.time.formatFragmentRecordedAtText
import com.example.moment.domain.time.parseFragmentRecordedAtText
import com.example.moment.domain.time.resolveFragmentRecordedAtForSave
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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val userPreferencesRepository: UserPreferencesRepository,
    private val nasArchiveRepository: NasArchiveRepository,
    observeFragmentsForDate: ObserveFragmentsForDateUseCase,
    observeDiaryEntries: ObserveDiaryEntriesUseCase,
    private val fragmentLocationCapture: FragmentLocationCapture,
    savedStateHandle: SavedStateHandle,
    private val zoneId: ZoneId,
    private val clock: Clock
) : ViewModel() {

    private val _nasArchiveConflict = MutableStateFlow<NasArchiveConflictInfo?>(null)
    val nasArchiveConflictInfo: StateFlow<NasArchiveConflictInfo?> =
        _nasArchiveConflict.asStateFlow()

    private var nasArchiveConflictContinuation: CancellableContinuation<NasArchiveConflictChoice>? = null

    override fun onCleared() {
        nasArchiveConflictContinuation?.cancel()
        super.onCleared()
    }

    fun resolveNasArchiveConflict(choice: NasArchiveConflictChoice) {
        val c = nasArchiveConflictContinuation ?: return
        nasArchiveConflictContinuation = null
        _nasArchiveConflict.value = null
        c.resumeWith(Result.success(choice))
    }

    fun refreshNasArchivePull() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.preferences.first()
            if (!prefs.nasArchiveSyncEnabled) {
                _uiState.update {
                    it.copy(nasArchiveSyncMessage = "请先在设置中开启「保存后自动同步到 NAS 存档」")
                }
                return@launch
            }
            val cfg = prefs.toNasWebdavConfig()
            if (!cfg.isConfigured()) {
                _uiState.update {
                    it.copy(nasArchiveSyncMessage = "请先在设置中填写并保存 WebDAV 地址")
                }
                return@launch
            }
            _uiState.update {
                it.copy(nasArchiveRefreshing = true, nasArchiveSyncMessage = null)
            }
            val r = runCatching {
                nasArchiveRepository.pullArchiveToLocal(cfg) { info ->
                    withContext(Dispatchers.Main.immediate) {
                        suspendCancellableCoroutine { cont ->
                            nasArchiveConflictContinuation = cont
                            _nasArchiveConflict.value = info
                        }
                    }
                }
            }.getOrElse { Result.failure(it) }
            _uiState.update {
                it.copy(nasArchiveRefreshing = false)
            }
            _uiState.update {
                it.copy(
                    nasArchiveSyncMessage = r.fold(
                        onSuccess = { s ->
                            "已同步存档：处理 ${s.diariesApplied} 日，跳过 ${s.diariesSkipped}，图片 ${s.imagesRestored} 张"
                        },
                        onFailure = { e ->
                            "存档同步失败：${e.message ?: e.javaClass.simpleName}"
                        }
                    )
                )
            }
        }
    }
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
            val base = _uiState.value
            // 根路由新建碎片：摘要日/「当天手帐」始终以 clock 为准，避免 contextDay 在午夜后仍停留在昨天。
            val summaryDay: LocalDate? = when {
                base.editingFragmentId > 0L -> dayNullable
                newFragmentForDate != null -> newFragmentForDate
                else -> clock.instant().atZone(zoneId).toLocalDate()
            }
            if (summaryDay == null) {
                flowOf(
                    base.copy(
                        summaryCalendarDay = null,
                        otherFragmentsOnDay = emptyList(),
                        canGenerateDiary = false,
                        savedDiaryEntries = emptyList()
                    )
                )
            } else {
                observeFragmentsForDate(summaryDay).map { fragments ->
                    val s = _uiState.value
                    s.copy(
                        summaryCalendarDay = summaryDay,
                        otherFragmentsOnDay = if (s.editingFragmentId > 0) {
                            fragments.filter { it.id != s.editingFragmentId }
                        } else {
                            fragments
                        },
                        canGenerateDiary = fragments.isNotEmpty(),
                        savedDiaryEntries = diaryEntries.filter { it.date == summaryDay }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    init {
        val id = savedStateHandle.get<Long>(ARG_FRAGMENT_ID) ?: 0L
        if (id > 0) {
            _uiState.update { it.copy(editingFragmentId = id, editingFragmentStableId = "", isLoadingDraft = true) }
            viewModelScope.launch {
                runCatching { getFragmentById(id) }
                    .onSuccess { fragment ->
                        if (fragment != null) {
                            contextDay.value = fragment.createdAt.atZone(zoneId).toLocalDate()
                            val recordedAtText = formatFragmentRecordedAtText(fragment.createdAt, zoneId)
                            _uiState.update {
                                it.copy(
                                    isLoadingDraft = false,
                                    editingFragmentStableId = fragment.stableId,
                                    content = fragment.content,
                                    tags = fragment.tags.joinToString(", "),
                                    imageUris = fragment.imageUris.joinToString(", "),
                                    mood = fragment.mood,
                                    recordedDate = recordedAtText.date,
                                    recordedTime = recordedAtText.time,
                                    baselineRecordedDate = recordedAtText.date,
                                    baselineRecordedTime = recordedAtText.time,
                                    baselineRecordedAt = fragment.createdAt,
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
        } else {
            val recordedAt = resolveNewFragmentRecordedAt(clock, zoneId, newFragmentForDate) ?: clock.instant()
            val recordedAtText = formatFragmentRecordedAtText(recordedAt, zoneId)
            _uiState.update {
                it.copy(
                    recordedDate = recordedAtText.date,
                    recordedTime = recordedAtText.time,
                    baselineRecordedDate = recordedAtText.date,
                    baselineRecordedTime = recordedAtText.time,
                    baselineRecordedAt = recordedAt
                )
            }
        }
    }

    fun updateContent(value: String) = _uiState.update { it.copy(content = value, errorMessage = null) }
    fun updateTags(value: String) = _uiState.update { it.copy(tags = value) }
    fun updateImageUris(value: String) = _uiState.update { it.copy(imageUris = value) }
    fun updateRecordedDate(value: String) = _uiState.update { it.copy(recordedDate = value, errorMessage = null) }
    fun updateRecordedTime(value: String) = _uiState.update { it.copy(recordedTime = value, errorMessage = null) }

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
                    val mergedTags =
                        (state.tags.csvValues() + suggestion.suggestedTags)
                            .map { t -> t.trim() }
                            .filter { t -> t.isNotEmpty() }
                            .distinct()
                    state.copy(
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
            val currentRecordedAtText = FragmentRecordedAtText(
                date = state.recordedDate,
                time = state.recordedTime
            )
            val parsedRecordedAt = parseFragmentRecordedAtText(
                dateText = state.recordedDate,
                timeText = state.recordedTime,
                zoneId = zoneId
            )
            if (parsedRecordedAt == null) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "请填写正确的记录时间（日期 2026-05-13，时间 22:30）")
                }
                return@launch
            }
            val recordedAt = resolveFragmentRecordedAtForSave(
                parsedRecordedAt = parsedRecordedAt,
                currentText = currentRecordedAtText,
                baselineRecordedAt = state.baselineRecordedAt,
                baselineText = state.baselineRecordedText()
            )
            runCatching {
                if (state.editingFragmentId > 0) {
                    when (
                        updateFragment(
                            id = state.editingFragmentId,
                            content = state.content,
                            imageUris = state.imageUris.csvValues(),
                            mood = state.mood,
                            tags = state.tags.csvValues(),
                            recordedAt = recordedAt,
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

    fun clearNasArchiveSyncMessage() {
        _uiState.update { it.copy(nasArchiveSyncMessage = null) }
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
    /** 与地点编辑路由一致；新建碎片时为空直到首次保存。 */
    val editingFragmentStableId: String = "",
    val isLoadingDraft: Boolean = false,
    val content: String = "",
    val tags: String = "",
    val imageUris: String = "",
    val mood: Mood? = null,
    val recordedDate: String = "",
    val recordedTime: String = "",
    val baselineRecordedDate: String = "",
    val baselineRecordedTime: String = "",
    val baselineRecordedAt: java.time.Instant? = null,
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
    val savedDiaryEntries: List<DiaryEntry> = emptyList(),
    val nasArchiveRefreshing: Boolean = false,
    val nasArchiveSyncMessage: String? = null
) {
    fun baselineRecordedText(): FragmentRecordedAtText? =
        if (baselineRecordedDate.isNotBlank() && baselineRecordedTime.isNotBlank()) {
            FragmentRecordedAtText(date = baselineRecordedDate, time = baselineRecordedTime)
        } else {
            null
        }
}
