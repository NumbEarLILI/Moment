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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onClose) { Text("返回") }
            Text("生成手帐", style = MaterialTheme.typography.headlineSmall)
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") }
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("正文") }
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
                    Text("今日亮点：${state.highlights.joinToString(" / ")}")
                }
                state.moodSummary?.let { Text(it) }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving && state.sourceFragmentIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isSaving) "保存中..." else "保存日记")
                }
            }
        }
    }
}
