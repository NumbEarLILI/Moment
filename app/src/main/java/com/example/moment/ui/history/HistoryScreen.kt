package com.example.moment.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.moment.ui.common.FragmentWeatherAndPlace
import com.example.moment.ui.common.MoodBadge
import com.example.moment.ui.common.MonthCalendar
import com.example.moment.ui.common.QuietTextAction
import com.example.moment.ui.common.TagLine
import com.example.moment.ui.diary.DiarySummaryCard
import com.example.moment.ui.theme.MomentHairline
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
    onGenerateDiary: (LocalDate) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            QuietTextAction(
                                text = "补记",
                                onClick = { onAddFragmentForPastDay(state.selectedDate) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        QuietTextAction(
                            text = "生成手帐",
                            onClick = { onGenerateDiary(state.selectedDate) },
                            enabled = state.canGenerateDiary
                        )
                    }
                }
            }
            item {
                MonthCalendar(
                    visibleMonth = state.visibleMonth,
                    selectedDate = state.selectedDate,
                    today = viewModel.today,
                    datesWithSavedDiary = state.datesWithSavedDiary,
                    onDayClick = viewModel::onCalendarDayClick,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth
                )
            }
            item { MomentHairline() }
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
                else -> itemsIndexed(
                    items = state.fragments,
                    key = { _, fragment -> "fragment-${fragment.id}" }
                ) { index, fragment ->
                    Column(Modifier.fillMaxWidth()) {
                        FragmentCard(
                            fragment = fragment,
                            onContinueEdit = { onContinueEditFragment(fragment.id) },
                            onDelete = { viewModel.delete(fragment.id) }
                        )
                        if (index < state.fragments.lastIndex) {
                            MomentHairline(Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            item { MomentHairline() }
            item {
                Text(
                    "手帐",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.diaryEntries.isEmpty()) {
                item {
                    Text(
                        "这一天还没有手帐。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(
                    items = state.diaryEntries,
                    key = { _, entry -> "diary-${entry.id}" }
                ) { index, entry ->
                    Column(Modifier.fillMaxWidth()) {
                        DiarySummaryCard(entry = entry, onClick = { onOpenDiary(entry.id) })
                        if (index < state.diaryEntries.lastIndex) {
                            MomentHairline()
                        }
                    }
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
            QuietTextAction(text = "编辑", onClick = onContinueEdit)
            QuietTextAction(
                text = "删除",
                onClick = onDelete,
                color = MaterialTheme.colorScheme.error
            )
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
        FragmentWeatherAndPlace(
            weather = fragment.weather,
            location = fragment.location
        )
        if (fragment.imageUris.isNotEmpty()) {
            Text(
                "${fragment.imageUris.size} 张图片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

