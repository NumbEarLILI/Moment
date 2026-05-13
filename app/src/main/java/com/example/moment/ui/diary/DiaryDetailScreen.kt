package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiaryDetailScreen(
    onBack: () -> Unit,
    viewModel: DiaryDetailViewModel = hiltViewModel()
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
            when {
                state.isLoading -> CircularProgressIndicator()
                state.entry == null -> Text("没有找到这篇日记。")
                else -> {
                    val entry = checkNotNull(state.entry)
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(entry.date.toString(), color = MaterialTheme.colorScheme.secondary)
                    Text(entry.body)
                    if (entry.highlights.isNotEmpty()) {
                        Text("亮点：${entry.highlights.joinToString(" / ")}")
                    }
                    entry.moodSummary?.let { Text(it) }
                }
            }
        }
    }
}
