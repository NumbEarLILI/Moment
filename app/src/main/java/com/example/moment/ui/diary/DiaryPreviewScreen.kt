package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                DiaryEditorForm(
                    state = state,
                    headline = "生成手帐",
                    saveButtonLabel = "保存日记",
                    placePickDiaryId = diaryId,
                    navController = navController,
                    onTitleChange = viewModel::updateTitle,
                    onBodyChange = viewModel::updateBody,
                    onSave = viewModel::save
                )
            }
        }
    }
}
