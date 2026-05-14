package com.example.moment.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
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
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.Routes
import com.example.moment.ui.place.MOMENT_PICK_LOCATION_JSON_KEY
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ImageThumbSize = 88.dp
private val HeaderDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@Composable
fun CaptureScreen(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    onClose: () -> Unit,
    onGenerateDiary: (LocalDate) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickJson by backStackEntry.savedStateHandle
        .getStateFlow(MOMENT_PICK_LOCATION_JSON_KEY, "")
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
    var momentExpanded by remember { mutableStateOf(false) }
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

    LaunchedEffect(pickJson) {
        if (pickJson.isNotBlank()) {
            viewModel.applyPickedLocationFromJson(pickJson)
            backStackEntry.savedStateHandle[MOMENT_PICK_LOCATION_JSON_KEY] = ""
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                CaptureHeader(
                    isEditing = state.editingFragmentId > 0,
                    selectedDate = state.summaryCalendarDay,
                    canGenerateDiary = state.canGenerateDiary,
                    onGenerateDiary = { state.summaryCalendarDay?.let(onGenerateDiary) },
                    onOpenHistory = { navController.navigate(Routes.History) },
                    onClose = onClose,
                    momentExpanded = momentExpanded,
                    onToggleMomentExpanded = { momentExpanded = !momentExpanded },
                    momentContent = state.content,
                    onMomentContentChange = viewModel::updateContent,
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
                                        state.editingFragmentId,
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
                    momentInteractionsEnabled = !state.isSaving && !state.isLoadingDraft
                )
                when {
                    state.isLoadingDraft ->
                        CircularProgressIndicator(Modifier.padding(20.dp))
                    else ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        state.summaryCalendarDay?.let { day ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                        shape = MaterialTheme.shapes.medium
                                    ),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${day.format(DateTimeFormatter.ISO_LOCAL_DATE)} · 已有记录",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (state.otherFragmentsOnDay.isEmpty()) {
                                        Text(
                                            if (state.editingFragmentId > 0) {
                                                "今日仅有正在编辑的这条记录，暂无其它已保存碎片。"
                                            } else {
                                                "这一天还没有其它已保存碎片，写完后保存即可新增一条。"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
                                    } else {
                                        Text(
                                            "以下为本日已保存内容，避免重复记录。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(0.dp)
                                        ) {
                                            state.otherFragmentsOnDay.forEachIndexed { index, fragment ->
                                                key(fragment.id) {
                                                    DayFragmentSummaryRow(
                                                        fragment = fragment,
                                                        onOpen = { navController.navigate(Routes.capture(fragment.id)) }
                                                    )
                                                    if (index < state.otherFragmentsOnDay.lastIndex) {
                                                        HorizontalDivider(
                                                            thickness = 0.5.dp,
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                                alpha = 0.45f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        val saveLabel = when {
                            state.isSaving -> "保存中..."
                            state.editingFragmentId > 0 -> "保存修改"
                            else -> "保存碎片"
                        }
                        Button(
                            onClick = { requestSave() },
                            enabled = !state.isSaving && !state.isLoadingDraft && !state.isAnalyzingImages,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(saveLabel)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
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
    }
}

@Composable
private fun CaptureHeader(
    isEditing: Boolean,
    selectedDate: LocalDate?,
    canGenerateDiary: Boolean,
    onGenerateDiary: () -> Unit,
    onOpenHistory: () -> Unit,
    onClose: () -> Unit,
    momentExpanded: Boolean,
    onToggleMomentExpanded: () -> Unit,
    momentContent: String,
    onMomentContentChange: (String) -> Unit,
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
) {
    val title = if (isEditing) "继续编辑碎片" else "记录生活碎片"
    val subtitle = selectedDate?.let { "${it.format(HeaderDateFormatter)} · 把这天整理成一页手帐" }
        ?: "随手记下文字、照片与地点"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Moment",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onClose,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CaptureMomentExpandable(
                expanded = momentExpanded,
                onToggleExpanded = onToggleMomentExpanded,
                content = momentContent,
                onContentChange = onMomentContentChange,
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
                interactionsEnabled = momentInteractionsEnabled
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onGenerateDiary,
                    enabled = canGenerateDiary,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("生成手帐")
                }
                OutlinedButton(
                    onClick = onOpenHistory,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("历史")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureMomentExpandable(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
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
) {
    val corner = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = corner
            ),
        shape = corner,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(corner)
                    .clickable(enabled = interactionsEnabled, onClick = onToggleExpanded)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "发生了什么？",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    when {
                        expanded -> Text(
                            "点按标题栏可收起",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                        content.isNotBlank() -> Text(
                            text = content.trim().replace("\n", " ").let {
                                if (it.length > 56) it.take(56) + "…" else it
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        else -> Text(
                            "点按展开，添加文字、照片、地点与标签",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                }
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                        placeholder = { Text("写下这一刻…") },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        "图片",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                                "正在自动识别图片并补充文案与标签",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (imageUriList.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ImageThumbSize),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(imageUriList, key = { it }) { uri ->
                                ImageThumbnail(
                                    uri = uri,
                                    onRemove = { onRemoveImage(uri) }
                                )
                            }
                        }
                    } else {
                        Text(
                            "使用相机或相册添加照片，添加后会自动识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onCamera,
                            enabled = interactionsEnabled && !isAnalyzingImages
                        ) {
                            Text("相机拍照")
                        }
                        TextButton(
                            onClick = onGallery,
                            enabled = interactionsEnabled && !isAnalyzingImages
                        ) {
                            Text("从相册选择")
                        }
                    }
                    Text(
                        "地点",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    location?.let { loc ->
                        Text(
                            "当前：${loc.label.orEmpty()}（${
                                String.format(
                                    Locale.CHINA,
                                    "%.4f，%.4f",
                                    loc.latitude,
                                    loc.longitude
                                )
                            }）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onPickPlace,
                        enabled = interactionsEnabled,
                        modifier = Modifier.padding(start = (-4).dp)
                    ) {
                        Text("在地图上选择地点名称")
                    }
                    Text(
                        "标签",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = onNewTagInputChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("自定义标签") },
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        TextButton(
                            onClick = onCommitNewTag,
                            enabled = interactionsEnabled && newTagInput.trim().isNotEmpty()
                        ) {
                            Text("添加")
                        }
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
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "×",
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun DayFragmentSummaryRow(
    fragment: LifeFragment,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fragment.createdAt.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            val preview = fragment.content.trim().ifBlank { "（无文字）" }
                .let { if (it.length > 120) it.take(120) + "…" else it }
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(
            onClick = onOpen,
            shape = MaterialTheme.shapes.small
        ) {
            Text("查看", color = MaterialTheme.colorScheme.primary)
        }
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
