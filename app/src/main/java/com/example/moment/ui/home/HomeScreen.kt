package com.example.moment.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onAddFragment: () -> Unit,
    onGenerateDiary: (LocalDate) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Text("Moment", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(viewModel.today.format(DateTimeFormatter.ISO_DATE), color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddFragment) {
                    Text("记录碎片")
                }
                OutlinedButton(onClick = { onGenerateDiary(viewModel.today) }) {
                    Text("生成手帐")
                }
                TextButton(onClick = onOpenHistory) {
                    Text("历史")
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Text(state.errorMessage ?: "")
                state.fragments.isEmpty() -> EmptyToday()
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.fragments, key = { it.id }) { fragment ->
                        FragmentCard(fragment = fragment, onDelete = { viewModel.delete(fragment.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyToday() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今天还没有记录", style = MaterialTheme.typography.titleMedium)
            Text("写下一句话、一种心情，晚上就能生成一篇日记手帐。")
        }
    }
}

@Composable
private fun FragmentCard(fragment: LifeFragment, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fragment.createdAt.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
            if (fragment.content.isNotBlank()) {
                Text(fragment.content)
            }
            fragment.mood?.let { Text("心情：${it.displayName}") }
            if (fragment.tags.isNotEmpty()) {
                Text(fragment.tags.joinToString(prefix = "#", separator = " #"))
            }
            if (fragment.imageUris.isNotEmpty()) {
                Text("图片：${fragment.imageUris.size} 张")
            }
        }
    }
}
