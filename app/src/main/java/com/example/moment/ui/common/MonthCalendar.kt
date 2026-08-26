package com.example.moment.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MonthCalendar(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithRecords: Set<LocalDate> = emptySet(),
    onDayClick: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weekFields = WeekFields.of(Locale.getDefault())
    val weekDayLabels = remember(weekFields) {
        List(7) { i ->
            weekFields.firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
    val cells = remember(visibleMonth, weekFields) { calendarCellsForMonth(visibleMonth, weekFields) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPreviousMonth) {
                Text("‹", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
            }
            Text(
                "${visibleMonth.year}年${visibleMonth.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            TextButton(onClick = onNextMonth) {
                Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
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
                        hasRecord = date != null && date in datesWithRecords,
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
    hasRecord: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (date == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val scheme = MaterialTheme.colorScheme
    val isSelected = date == selectedDate
    val isToday = date == today
    val bg = when {
        isSelected -> scheme.primary.copy(alpha = 0.28f)
        hasRecord -> scheme.primaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected || hasRecord) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isSelected || isToday -> scheme.primary
                hasRecord -> scheme.onPrimaryContainer
                else -> scheme.onSurface
            }
        )
    }
}
