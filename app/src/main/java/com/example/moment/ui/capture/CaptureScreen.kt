package com.example.moment.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.ui.Routes
import com.example.moment.ui.place.MOMENT_PICK_LOCATION_JSON_KEY
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ImageThumbSize = 88.dp

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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                    tonalElevation = 2.dp
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (state.editingFragmentId > 0) "继续编辑碎片" else "记录生活碎片",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { state.summaryCalendarDay?.let(onGenerateDiary) },
                                enabled = state.canGenerateDiary,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("生成手帐")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(
                                    onClick = { navController.navigate(Routes.History) },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("历史", color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = onClose,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "${day.format(DateTimeFormatter.ISO_LOCAL_DATE)} · 已有记录",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (state.otherFragmentsOnDay.isEmpty()) {
                                        Text(
                                            if (state.editingFragmentId > 0) {
                                                "今日仅有正在编辑的这条记录，暂无其它已保存碎片。"
                                            } else {
                                                "这一天还没有其它已保存碎片，写完后保存即可新增一条。"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            "以下为本日已保存内容，避免重复记录。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        state.otherFragmentsOnDay.forEach { fragment ->
                                            key(fragment.id) {
                                                DayFragmentSummaryRow(
                                                    fragment = fragment,
                                                    onOpen = { navController.navigate(Routes.capture(fragment.id)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.content,
                            onValueChange = viewModel::updateContent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            label = { Text("发生了什么？") },
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text("心情", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mood.entries.forEach { mood ->
                                FilterChip(
                                    selected = state.mood == mood,
                                    onClick = { viewModel.updateMood(if (state.mood == mood) null else mood) },
                                    label = { Text(mood.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = MaterialTheme.shapes.small
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.tags,
                            onValueChange = viewModel::updateTags,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("标签，用英文逗号分隔") },
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text("地点", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        val loc = state.locationOverride ?: state.baselineLocation
                        loc?.let {
                            Text(
                                "当前：${it.label.orEmpty()}（${
                                    String.format(
                                        Locale.CHINA,
                                        "%.4f，%.4f",
                                        it.latitude,
                                        it.longitude
                                    )
                                }）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
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
                            enabled = !state.isSaving && !state.isLoadingDraft
                        ) {
                            Text("在地图上选择地点名称")
                        }
                        Text("图片", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                                        onRemove = { viewModel.removeImageUri(uri) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                "点击下方添加照片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val uri = createCameraImageUri(context)
                                    pendingCameraUri = uri
                                    takePicture.launch(uri)
                                },
                                enabled = !state.isAnalyzingImages
                            ) {
                                Text("相机拍照")
                            }
                            TextButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = !state.isAnalyzingImages
                            ) {
                                Text("从相册选择")
                            }
                        }
                        if (imageUriList.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = viewModel::suggestCaptionFromSelectedImages,
                                    enabled = !state.isAnalyzingImages && !state.isSaving
                                ) {
                                    Text("识别图片并生成文案")
                                }
                                if (state.isAnalyzingImages) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
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
private fun DayFragmentSummaryRow(
    fragment: LifeFragment,
    onOpen: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpen, shape = MaterialTheme.shapes.small) { Text("查看") }
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
