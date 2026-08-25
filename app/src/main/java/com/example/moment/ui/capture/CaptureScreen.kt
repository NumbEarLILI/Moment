package com.example.moment.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.ui.Routes
import com.example.moment.ui.diary.DiarySummaryCard
import com.example.moment.ui.theme.appScaffoldContainerColor
import com.example.moment.ui.theme.momentTransparentTextFieldColors
import com.example.moment.ui.place.MOMENT_PICK_LOCATION_JSON_KEY
import com.example.moment.ui.timeline.FragmentTimelineSection
import com.example.moment.ui.timeline.FragmentTimelineViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ImageThumbSize = 88.dp
private val HeaderDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private const val CAPTURE_MOMENT_EXPANDED_KEY = "captureMomentExpanded"

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CaptureScreen(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    onClose: () -> Unit,
    onGenerateDiary: (LocalDate) -> Unit,
    onOpenDiary: (Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
    timelineViewModel: FragmentTimelineViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timelineState by timelineViewModel.uiState.collectAsStateWithLifecycle()
    val nasArchiveConflict by viewModel.nasArchiveConflictInfo.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.nasArchiveRefreshing,
        onRefresh = viewModel::refreshNasArchivePull
    )
    val pickJson by backStackEntry.savedStateHandle
        .getStateFlow(MOMENT_PICK_LOCATION_JSON_KEY, "")
        .collectAsStateWithLifecycle()
    val navFragmentId = backStackEntry.arguments?.getLong("fragmentId") ?: 0L
    val momentExpanded by backStackEntry.savedStateHandle
        .getStateFlow(CAPTURE_MOMENT_EXPANDED_KEY, navFragmentId > 0L)
        .collectAsStateWithLifecycle()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSaveAfterLocationPermission by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val imageUriList = remember(state.imageUris) {
        state.imageUris.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val tagList = remember(state.tags) {
        state.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    var newTagInput by remember { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (pendingSaveAfterLocationPermission) {
            pendingSaveAfterLocationPermission = false
        }
        viewModel.save()
    }

    var showPlacePickPermissionDialog by remember { mutableStateOf(false) }
    var pendingPlacePickAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val placePickPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val action = pendingPlacePickAction
        pendingPlacePickAction = null
        showPlacePickPermissionDialog = false
        action?.invoke()
    }

    fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    fun requestSave() {
        if (state.editingFragmentId > 0L || hasLocationPermission()) {
            viewModel.save()
        } else {
            pendingSaveAfterLocationPermission = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.addImageUris(uris.map { it.toString() })
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            viewModel.addImageUris(listOf(uri.toString()))
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    LaunchedEffect(state.nasArchiveSyncMessage) {
        if (state.nasArchiveSyncMessage != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearNasArchiveSyncMessage()
        }
    }

    LaunchedEffect(pickJson) {
        if (pickJson.isNotBlank()) {
            viewModel.applyPickedLocationFromJson(pickJson)
            backStackEntry.savedStateHandle[MOMENT_PICK_LOCATION_JSON_KEY] = ""
            backStackEntry.savedStateHandle[CAPTURE_MOMENT_EXPANDED_KEY] = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
            Scaffold(
                containerColor = appScaffoldContainerColor(),
                contentColor = MaterialTheme.colorScheme.onBackground
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(scrollState)
                ) {
                    state.nasArchiveSyncMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                    CaptureHeader(
                    selectedDate = state.summaryCalendarDay,
                    canGenerateDiary = state.canGenerateDiary,
                    onGenerateDiary = { state.summaryCalendarDay?.let(onGenerateDiary) },
                    momentExpanded = momentExpanded,
                    onToggleMomentExpanded = {
                        val cur = backStackEntry.savedStateHandle[CAPTURE_MOMENT_EXPANDED_KEY] ?: false
                        backStackEntry.savedStateHandle[CAPTURE_MOMENT_EXPANDED_KEY] = !cur
                    },
                    momentContent = state.content,
                    onMomentContentChange = viewModel::updateContent,
                    recordedDate = state.recordedDate,
                    onRecordedDateChange = viewModel::updateRecordedDate,
                    recordedTime = state.recordedTime,
                    onRecordedTimeChange = viewModel::updateRecordedTime,
                    tagList = tagList,
                    onRemoveTag = viewModel::removeTag,
                    newTagInput = newTagInput,
                    onNewTagInputChange = { newTagInput = it },
                    onCommitNewTag = {
                        viewModel.addTag(newTagInput)
                        newTagInput = ""
                    },
                    imageUriList = imageUriList,
                    onRemoveImage = viewModel::removeImageUri,
                    onCamera = {
                        val uri = createCameraImageUri(context)
                        pendingCameraUri = uri
                        takePicture.launch(uri)
                    },
                    onGallery = { imagePicker.launch(arrayOf("image/*")) },
                    onPickPlace = {
                        fun navigateToPlacePick() {
                            viewModel.requestPlacePickSeed { lat, lng, hint ->
                                navController.navigate(
                                    Routes.placePick(
                                        lat,
                                        lng,
                                        hint,
                                        state.editingFragmentStableId,
                                        0L
                                    )
                                )
                            }
                        }
                        if (hasLocationPermission()) {
                            navigateToPlacePick()
                        } else {
                            pendingPlacePickAction = { navigateToPlacePick() }
                            showPlacePickPermissionDialog = true
                        }
                    },
                    location = state.locationOverride ?: state.baselineLocation,
                    isAnalyzingImages = state.isAnalyzingImages,
                    momentInteractionsEnabled = !state.isSaving && !state.isLoadingDraft && !state.isDeleting,
                    canDeleteFragment = state.editingFragmentId > 0,
                    isDeleting = state.isDeleting,
                    onRequestDelete = { showDeleteConfirmDialog = true },
                    errorMessage = state.errorMessage,
                    saveLabel = when {
                        state.isSaving -> "保存中..."
                        state.editingFragmentId > 0 -> "保存修改"
                        else -> "保存碎片"
                    },
                    onSave = { requestSave() },
                    saveEnabled = !state.isSaving && !state.isLoadingDraft && !state.isAnalyzingImages && !state.isDeleting
                )
                when {
                    state.isLoadingDraft ->
                        CircularProgressIndicator(Modifier.padding(20.dp))
                    else ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        if (state.savedDiaryEntries.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "当天手帐",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                state.savedDiaryEntries.forEach { entry ->
                                    key(entry.id) {
                                        DiarySummaryCard(
                                            entry = entry,
                                            onClick = { onOpenDiary(entry.id) }
                                        )
                                    }
                                }
                            }
                        }
                        FragmentTimelineSection(
                            state = timelineState,
                            onAddFragment = {
                                backStackEntry.savedStateHandle[CAPTURE_MOMENT_EXPANDED_KEY] = true
                            },
                            onContinueEditFragment = { id -> navController.navigate(Routes.capture(id)) },
                            onOpenDiary = onOpenDiary,
                            onDeleteFragment = timelineViewModel::delete,
                            onClearDeleteError = timelineViewModel::clearDeleteError,
                            hiddenFragmentId = state.editingFragmentId.takeIf { it > 0L },
                            hiddenDiaryFallbackDate = state.summaryCalendarDay
                        )
                    }
                }
                }
            }
        PullRefreshIndicator(
            refreshing = state.nasArchiveRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    nasArchiveConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = {
                viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.KEEP_LOCAL)
            },
            title = { Text("NAS 存档冲突") },
            text = {
                Text(
                    "「${conflict.date}」手帐：本机修改时间更新，但与 NAS 正文不一致。\n\n" +
                        "本机标题：${conflict.localTitle}\nNAS 标题：${conflict.remoteTitle}\n\n保留本机还是用 NAS 覆盖？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.USE_REMOTE)
                    }
                ) {
                    Text("使用 NAS")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.KEEP_LOCAL)
                    }
                ) {
                    Text("保留本地")
                }
            }
        )
    }
    if (showPlacePickPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPlacePickPermissionDialog = false
                pendingPlacePickAction = null
            },
            title = { Text("需要定位权限") },
            text = {
                Text(
                    "在地图上选点前需要定位权限，用于确定地图中心；保存碎片时也会写入位置信息。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlacePickPermissionDialog = false
                        placePickPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                ) { Text("去授权") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPlacePickPermissionDialog = false
                        pendingPlacePickAction = null
                    }
                ) { Text("取消") }
            }
        )
    }
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) showDeleteConfirmDialog = false },
            title = { Text("删除碎片") },
            text = {
                Text(
                    "确定要删除这条碎片吗？此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteEditingFragment()
                    },
                    enabled = !state.isDeleting
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !state.isDeleting
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CaptureHeader(
    selectedDate: LocalDate?,
    canGenerateDiary: Boolean,
    onGenerateDiary: () -> Unit,
    momentExpanded: Boolean,
    onToggleMomentExpanded: () -> Unit,
    momentContent: String,
    onMomentContentChange: (String) -> Unit,
    recordedDate: String,
    onRecordedDateChange: (String) -> Unit,
    recordedTime: String,
    onRecordedTimeChange: (String) -> Unit,
    tagList: List<String>,
    onRemoveTag: (String) -> Unit,
    newTagInput: String,
    onNewTagInputChange: (String) -> Unit,
    onCommitNewTag: () -> Unit,
    imageUriList: List<String>,
    onRemoveImage: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPickPlace: () -> Unit,
    location: FragmentLocation?,
    isAnalyzingImages: Boolean,
    momentInteractionsEnabled: Boolean,
    errorMessage: String?,
    saveLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    canDeleteFragment: Boolean,
    isDeleting: Boolean,
    onRequestDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selectedDate?.format(HeaderDateFormatter) ?: "此刻",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onGenerateDiary,
                enabled = canGenerateDiary
            ) {
                Text("生成手帐")
            }
        }
        CaptureMomentExpandable(
            expanded = momentExpanded,
            onToggleExpanded = onToggleMomentExpanded,
            content = momentContent,
            onContentChange = onMomentContentChange,
            recordedDate = recordedDate,
            onRecordedDateChange = onRecordedDateChange,
            recordedTime = recordedTime,
            onRecordedTimeChange = onRecordedTimeChange,
            tagList = tagList,
            onRemoveTag = onRemoveTag,
            newTagInput = newTagInput,
            onNewTagInputChange = onNewTagInputChange,
            onCommitNewTag = onCommitNewTag,
            imageUriList = imageUriList,
            onRemoveImage = onRemoveImage,
            onCamera = onCamera,
            onGallery = onGallery,
            onPickPlace = onPickPlace,
            location = location,
            isAnalyzingImages = isAnalyzingImages,
            interactionsEnabled = momentInteractionsEnabled,
            errorMessage = errorMessage,
            saveLabel = saveLabel,
            onSave = onSave,
            saveEnabled = saveEnabled,
            canDeleteFragment = canDeleteFragment,
            isDeleting = isDeleting,
            onRequestDelete = onRequestDelete
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureMomentExpandable(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    recordedDate: String,
    onRecordedDateChange: (String) -> Unit,
    recordedTime: String,
    onRecordedTimeChange: (String) -> Unit,
    tagList: List<String>,
    onRemoveTag: (String) -> Unit,
    newTagInput: String,
    onNewTagInputChange: (String) -> Unit,
    onCommitNewTag: () -> Unit,
    imageUriList: List<String>,
    onRemoveImage: (String) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPickPlace: () -> Unit,
    location: FragmentLocation?,
    isAnalyzingImages: Boolean,
    interactionsEnabled: Boolean,
    errorMessage: String?,
    saveLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    canDeleteFragment: Boolean,
    isDeleting: Boolean,
    onRequestDelete: () -> Unit,
) {
    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(content, selection = TextRange(content.length)))
    }
    LaunchedEffect(content) {
        if (content != contentFieldValue.text) {
            contentFieldValue = TextFieldValue(content, selection = TextRange(content.length))
        }
    }
    val fieldColors = momentTransparentTextFieldColors()
    Column(Modifier.fillMaxWidth()) {
        if (!expanded) {
            val preview = content.trim().replace("\n", " ").let {
                if (it.length > 56) it.take(56) + "…" else it
            }
            Text(
                text = if (content.isNotBlank()) preview else "写下这一刻…",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = interactionsEnabled, onClick = onToggleExpanded)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (content.isNotBlank()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            val thumbRowScroll = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onToggleExpanded,
                        enabled = interactionsEnabled
                    ) {
                        Text("收起")
                    }
                }
                TextField(
                    value = contentFieldValue,
                    onValueChange = { value ->
                        contentFieldValue = value
                        if (value.text != content) onContentChange(value.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    placeholder = { Text("写下这一刻…") },
                    colors = fieldColors
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = recordedDate,
                        onValueChange = onRecordedDateChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("日期") },
                        placeholder = { Text("2026-05-13") },
                        colors = fieldColors
                    )
                    TextField(
                        value = recordedTime,
                        onValueChange = onRecordedTimeChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("时间") },
                        placeholder = { Text("22:30") },
                        colors = fieldColors
                    )
                }
                if (isAnalyzingImages) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "正在自动识别图片并生成标签",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (imageUriList.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(thumbRowScroll)
                                .height(ImageThumbSize),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            imageUriList.forEach { uri ->
                                key(uri) {
                                    ImageThumbnail(
                                        uri = uri,
                                        onRemove = { onRemoveImage(uri) }
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onCamera,
                            enabled = interactionsEnabled && !isAnalyzingImages
                        ) {
                            Text("相机")
                        }
                        TextButton(
                            onClick = onGallery,
                            enabled = interactionsEnabled && !isAnalyzingImages
                        ) {
                            Text("相册")
                        }
                        TextButton(
                            onClick = onPickPlace,
                            enabled = interactionsEnabled
                        ) {
                            Text("地点")
                        }
                    }
                    location?.let { loc ->
                        Text(
                            loc.label?.takeIf { it.isNotBlank() } ?: String.format(
                                Locale.CHINA,
                                "%.4f，%.4f",
                                loc.latitude,
                                loc.longitude
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tagList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagList.forEach { tag ->
                                key(tag) {
                                    TagCapsule(text = tag, onRemove = { onRemoveTag(tag) })
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextField(
                            value = newTagInput,
                            onValueChange = onNewTagInputChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("标签") },
                            colors = fieldColors
                        )
                        TextButton(
                            onClick = onCommitNewTag,
                            enabled = interactionsEnabled && newTagInput.trim().isNotEmpty()
                        ) {
                            Text("添加")
                        }
                    }
                    errorMessage?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(saveLabel)
                    }
                    if (canDeleteFragment) {
                        TextButton(
                            onClick = onRequestDelete,
                            enabled = interactionsEnabled && !isDeleting && !isAnalyzingImages,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isDeleting) "删除中…" else "删除碎片",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun TagCapsule(
    text: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "#$text",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "×",
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImageThumbnail(
    uri: String,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(ImageThumbSize)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = CircleShape
                )
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun createCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
