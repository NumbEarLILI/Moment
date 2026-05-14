package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.moment.ui.Routes
import com.example.moment.ui.place.MOMENT_PICK_LOCATION_JSON_KEY

@Composable
fun DiaryPreviewScreen(
    navController: NavHostController,
    previewBackStackEntry: NavBackStackEntry,
    diaryId: Long,
    onClose: () -> Unit,
    viewModel: DiaryPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickJson by previewBackStackEntry.savedStateHandle
        .getStateFlow(MOMENT_PICK_LOCATION_JSON_KEY, "")
        .collectAsStateWithLifecycle()

    LaunchedEffect(pickJson) {
        if (pickJson.isNotBlank()) {
            viewModel.reloadDraft()
            previewBackStackEntry.savedStateHandle[MOMENT_PICK_LOCATION_JSON_KEY] = ""
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onClose, shape = MaterialTheme.shapes.small) {
                Text("返回", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "生成手帐",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (state.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("正文") },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors
                )
                DiaryLocationPinsRow(
                    pins = state.locationPins,
                    onPinClick = { pin ->
                        navController.navigate(
                            Routes.placePick(
                                pin.latitude,
                                pin.longitude,
                                pin.placeName,
                                pin.fragmentId,
                                diaryId
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DiaryImageGallery(imageUris = state.imageUris, modifier = Modifier.fillMaxWidth())
                if (state.highlights.isNotEmpty()) {
                    Text(
                        "今日亮点：${state.highlights.joinToString(" / ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.moodSummary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && state.sourceFragmentIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (state.isSaving) "保存中..." else "保存日记")
                }
            }
        }
    }
}
