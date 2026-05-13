package com.example.moment.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.Mood
import java.io.File

@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSaveAfterLocationPermission by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (pendingSaveAfterLocationPermission) {
            pendingSaveAfterLocationPermission = false
        }
        viewModel.save()
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.editingFragmentId > 0) "继续编辑碎片" else "记录生活碎片",
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = onClose) { Text("关闭") }
            }
            when {
                state.isLoadingDraft -> CircularProgressIndicator()
                else -> {
                    OutlinedTextField(
                        value = state.content,
                        onValueChange = viewModel::updateContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text("发生了什么？") }
                    )
                    Text("心情")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mood.entries.forEach { mood ->
                            FilterChip(
                                selected = state.mood == mood,
                                onClick = { viewModel.updateMood(if (state.mood == mood) null else mood) },
                                label = { Text(mood.displayName) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = viewModel::updateTags,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标签，用英文逗号分隔") }
                    )
                    OutlinedTextField(
                        value = state.imageUris,
                        onValueChange = viewModel::updateImageUris,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("图片 URI，用英文逗号分隔") }
                    )
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
                    if (state.imageUris.isNotBlank()) {
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
                    Spacer(Modifier.weight(1f))
                    val saveLabel = when {
                        state.isSaving -> "保存中..."
                        state.editingFragmentId > 0 -> "保存修改"
                        else -> "保存碎片"
                    }
                    Button(
                        onClick = { requestSave() },
                        enabled = !state.isSaving && !state.isLoadingDraft && !state.isAnalyzingImages,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(saveLabel)
                    }
                }
            }
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
