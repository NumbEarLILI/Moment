package com.example.moment.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HomeScreen(
    onAddFragment: (LocalDate) -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    onGenerateDiary: (LocalDate) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var recordMenuExpanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Text("Moment", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                state.selectedDate.format(DateTimeFormatter.ISO_DATE),
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                            Button(onClick = { recordMenuExpanded = true }) {
                                Text("记录碎片")
                                Text(" ▾", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(
                                expanded = recordMenuExpanded,
                                onDismissRequest = { recordMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("新建碎片") },
                                    onClick = {
                                        recordMenuExpanded = false
                                        onAddFragment(state.selectedDate)
                                    }
                                )
                                HorizontalDivider()
                                if (state.fragments.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("该日暂无已保存碎片") },
                                        onClick = { recordMenuExpanded = false },
                                        enabled = false
                                    )
                                } else {
                                    state.fragments.forEach { fragment ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    fragmentSummary(fragment),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            onClick = {
                                                recordMenuExpanded = false
                                                onContinueEditFragment(fragment.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { onGenerateDiary(state.selectedDate) },
                            enabled = state.fragments.isNotEmpty()
                        ) {
                            Text("生成手帐")
                        }
                        TextButton(onClick = onOpenHistory) {
                            Text("历史")
                        }
                    }
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
            Spacer(Modifier.height(16.dp))
            Text(
                "${state.selectedDate} 的碎片",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Text(state.errorMessage ?: "")
                state.fragments.isEmpty() -> EmptyDayHint(state.selectedDate, viewModel.today)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.fragments, key = { it.id }) { fragment ->
                        FragmentCard(
                            fragment = fragment,
                            onContinueEdit = { onContinueEditFragment(fragment.id) },
                            onDelete = { viewModel.delete(fragment.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun fragmentSummary(fragment: LifeFragment): String {
    val base = fragment.content.trim().ifBlank { "（无文字）" }
    return if (base.length > 48) base.take(48) + "…" else base
}

@Composable
private fun MonthCalendar(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val weekFields = WeekFields.of(Locale.getDefault())
    val weekDayLabels = remember(weekFields) {
        List(7) { i ->
            weekFields.firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
    val cells = remember(visibleMonth, weekFields) { calendarCellsForMonth(visibleMonth, weekFields) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPreviousMonth) { Text("‹") }
            Text(
                "${visibleMonth.year}年${visibleMonth.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onNextMonth) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        selectedDate = selectedDate,
                        today = today,
                        onClick = onDayClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun calendarCellsForMonth(month: YearMonth, weekFields: WeekFields): List<LocalDate?> {
    val firstOfMonth = month.atDay(1)
    val minDow = weekFields.firstDayOfWeek
    val offset = (firstOfMonth.dayOfWeek.value - minDow.value + 7) % 7
    val cells = mutableListOf<LocalDate?>()
    repeat(offset) { cells.add(null) }
    for (d in 1..month.lengthOfMonth()) {
        cells.add(month.atDay(d))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selectedDate: LocalDate,
    today: LocalDate,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (date == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val isSelected = date == selectedDate
    val isToday = date == today
    val shape = CircleShape
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun EmptyDayHint(selected: LocalDate, today: LocalDate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("这一天还没有记录", style = MaterialTheme.typography.titleMedium)
            val hint = if (selected == today) {
                "写下一句话、一种心情，晚上就能生成一篇日记手帐。点击日历某一天可查看或生成该日手帐。"
            } else {
                "可在「记录碎片」中新建，或点选别日。点击日历上的日期可打开该日手帐（已保存则直接查看，否则进入生成预览）。"
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
