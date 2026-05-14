package com.example.moment.ui.history

import androidx.compose.foundation.clickable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.MonthCalendar
import com.example.moment.ui.diary.DiaryImageGallery
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onAddFragmentForPastDay: (LocalDate) -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("返回") }
                    if (state.selectedDate != viewModel.today) {
                        OutlinedButton(onClick = { onAddFragmentForPastDay(state.selectedDate) }) {
                            Text("为该日新增碎片")
                        }
                    }
                }
                Text("历史与日历", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    state.selectedDate.format(DateTimeFormatter.ISO_DATE),
                    color = MaterialTheme.colorScheme.secondary
                )
                if (state.selectedDate == viewModel.today) {
                    Text(
                        "查看今天请返回首页；在首页记录新碎片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonthCalendar(
                            visibleMonth = state.visibleMonth,
                            selectedDate = state.selectedDate,
                            today = viewModel.today,
                            onDayClick = viewModel::onCalendarDayClick,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.selectedDate} 的碎片",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.errorMessage != null -> item { Text(state.errorMessage ?: "") }
                state.fragments.isEmpty() -> item {
                    EmptyDayHint(state.selectedDate, viewModel.today)
                }
                else -> items(state.fragments, key = { it.id }) { fragment ->
                    FragmentCard(
                        fragment = fragment,
                        onContinueEdit = { onContinueEditFragment(fragment.id) },
                        onDelete = { viewModel.delete(fragment.id) }
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "已保存的手帐",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            if (state.diaryEntries.isEmpty()) {
                item { Text("还没有保存过日记。") }
            } else {
                items(state.diaryEntries, key = { it.id }) { entry ->
                    DiaryCard(entry = entry, onClick = { onOpenDiary(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyDayHint(selected: LocalDate, today: LocalDate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("这一天还没有记录", style = MaterialTheme.typography.titleMedium)
            val hint = if (selected == today) {
                "返回首页即可写新碎片并生成手帐。在日历上点某一天可查看或生成该日手帐。"
            } else {
                "可使用右上角「为该日新增碎片」补记，或在日历上点选其它日期。点日历某一天可打开该日手帐（已保存则直接查看，否则进入生成预览）。"
            }
            Text(hint)
        }
    }
}

@Composable
private fun FragmentCard(
    fragment: LifeFragment,
    onContinueEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fragment.createdAt.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onContinueEdit) {
                    Text("继续编辑")
                }
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
            fragment.location?.let { loc ->
                val line = loc.label ?: "${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, " +
                    String.format(Locale.getDefault(), "%.4f", loc.longitude)
                Text("位置：$line", color = MaterialTheme.colorScheme.secondary)
            }
            if (fragment.imageUris.isNotEmpty()) {
                Text("图片：${fragment.imageUris.size} 张")
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
