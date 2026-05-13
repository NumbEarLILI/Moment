package com.example.moment.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.ui.diary.DiaryImageGallery

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDiary: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("历史日记", style = MaterialTheme.typography.headlineSmall)
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Text(state.errorMessage ?: "")
                state.entries.isEmpty() -> Text("还没有保存过日记。")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.entries, key = { it.id }) { entry ->
                        DiaryCard(entry = entry, onClick = { onOpenDiary(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(entry.title, fontWeight = FontWeight.Bold)
                Text(entry.date.toString(), color = MaterialTheme.colorScheme.secondary)
                Text(entry.body.take(80))
            }
            if (entry.imageUris.isNotEmpty()) {
                DiaryImageGallery(
                    imageUris = entry.imageUris,
                    modifier = Modifier.fillMaxWidth(),
                    showLabel = false,
                    thumbnailSize = 88.dp,
                    rowHeight = 100.dp
                )
            }
        }
    }
}
