package com.example.moment.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.moment.ui.diary.DiarySummaryCard
import com.example.moment.ui.theme.appScaffoldContainerColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFragmentForPastDay: (LocalDate) -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = appScaffoldContainerColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    tonalElevation = 1.dp
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onBack,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("返回", color = MaterialTheme.colorScheme.primary)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onOpenSettings,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("设置", color = MaterialTheme.colorScheme.primary)
                                }
                                if (state.selectedDate != viewModel.today) {
                                    OutlinedButton(
                                        onClick = { onAddFragmentForPastDay(state.selectedDate) },
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("为该日新增碎片")
                                    }
                                }
                            }
                        }
                        Text(
                            "历史与日历",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            state.selectedDate.format(DateTimeFormatter.ISO_DATE),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.selectedDate == viewModel.today) {
                            Text(
                                "查看今天请返回首页；在首页记录新碎片。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.selectedDate} 的碎片",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "已保存的手帐",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("这一天还没有记录", style = MaterialTheme.typography.titleMedium)
            val hint = if (selected == today) {
                "返回首页即可写新碎片并生成手帐。在日历上点某一天可查看或生成该日手帐。"
            } else {
                "可使用「为该日新增碎片」补记，或在日历上点选其它日期。点日历某一天可打开该日手帐（已保存则直接查看，否则进入生成预览）。"
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FragmentCard(
    fragment: LifeFragment,
    onContinueEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fragment.createdAt.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onContinueEdit, shape = MaterialTheme.shapes.small) {
                    Text("继续编辑")
                }
                TextButton(onClick = onDelete, shape = MaterialTheme.shapes.small) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            if (fragment.content.isNotBlank()) {
                Text(
                    fragment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            fragment.mood?.let { MoodBadge(mood = it) }
            if (fragment.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fragment.tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "#$tag",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            fragment.location?.let { loc ->
                val line = loc.label ?: "${String.format(Locale.getDefault(), "%.4f", loc.latitude)}, " +
                    String.format(Locale.getDefault(), "%.4f", loc.longitude)
                Text(
                    "位置：$line",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (fragment.imageUris.isNotEmpty()) {
                Text("图片：${fragment.imageUris.size} 张", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

