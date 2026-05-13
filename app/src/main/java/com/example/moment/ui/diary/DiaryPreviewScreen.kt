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

@Composable
fun DiaryPreviewScreen(
    onClose: () -> Unit,
    viewModel: DiaryPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                if (state.highlights.isNotEmpty()) {
                    Text("今日亮点：${state.highlights.joinToString(" / ")}")
                }
                state.moodSummary?.let { Text(it) }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isSaving) "保存中..." else "保存日记")
                }
            }
        }
    }
}
