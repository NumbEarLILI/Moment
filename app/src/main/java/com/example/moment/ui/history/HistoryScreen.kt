package com.example.moment.ui.history

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.MoodBadge
import com.example.moment.ui.common.MonthCalendar
import com.example.moment.ui.common.TagLine
import com.example.moment.ui.diary.DiarySummaryCard
import com.example.moment.ui.theme.appScaffoldContainerColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onAddFragmentForPastDay: (LocalDate) -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "历史",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (state.selectedDate != viewModel.today) {
                            TextButton(
                                onClick = { onAddFragmentForPastDay(state.selectedDate) }
                            ) {
                                Text("补记")
                            }
                        }
                    }
                    Text(
                        state.selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                MonthCalendar(
                    visibleMonth = state.visibleMonth,
                    selectedDate = state.selectedDate,
                    today = viewModel.today,
                    datesWithSavedDiary = state.diaryEntries.map { it.date }.toSet(),
                    onDayClick = viewModel::onCalendarDayClick,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth
                )
            }
            item {
                Text(
                    "碎片",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                state.isLoading -> item { CircularProgressIndicator() }
                state.errorMessage != null -> item { Text(state.errorMessage ?: "") }
                state.fragments.isEmpty() -> item {
                    EmptyDayHint(state.selectedDate, viewModel.today)
                }
                else -> items(state.fragments, key = { "fragment-${it.id}" }) { fragment ->
                    FragmentCard(
                        fragment = fragment,
                        onContinueEdit = { onContinueEditFragment(fragment.id) },
                        onDelete = { viewModel.delete(fragment.id) }
                    )
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "手帐",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.diaryEntries.isEmpty()) {
                item {
                    Text(
                        "还没有保存过日记。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.diaryEntries, key = { "diary-${it.id}" }) { entry ->
                    DiarySummaryCard(entry = entry, onClick = { onOpenDiary(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyDayHint(selected: LocalDate, today: LocalDate) {
    val hint = if (selected == today) {
        "这一天还没有记录。"
    } else {
        "这一天还没有记录，可以点右上角补记。"
    }
    Text(
        hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FragmentCard(
    fragment: LifeFragment,
    onContinueEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fragment.createdAt.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onContinueEdit) {
                Text("编辑")
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
        if (fragment.content.isNotBlank()) {
            Text(
                fragment.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
        fragment.mood?.let { MoodBadge(mood = it) }
        TagLine(tags = fragment.tags)
        fragment.location?.let { loc ->
            val line = loc.label ?: "${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, " +
                String.format(Locale.getDefault(), "%.4f", loc.longitude)
            Text(
                line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (fragment.imageUris.isNotEmpty()) {
            Text(
                "${fragment.imageUris.size} 张图片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

