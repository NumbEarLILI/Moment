package com.example.moment.ui.capture

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.Mood

@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.addImageUris(uris.map { it.toString() })
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
                Text("记录生活碎片", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("关闭") }
            }
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
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Text("从相册选择图片")
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "保存中..." else "保存碎片")
            }
        }
    }
}
