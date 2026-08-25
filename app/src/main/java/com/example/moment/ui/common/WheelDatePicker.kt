package com.example.moment.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val DateDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val WheelItemHeight = 40.dp
private const val VisibleWheelItems = 5

@Composable
fun WheelDateField(
    dateText: String,
    onDateTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    today: LocalDate = LocalDate.now()
) {
    val selected = parseIsoDateOrNull(dateText) ?: today
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { showSheet = true }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "日期",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            selected.format(DateDisplayFormatter),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showSheet) {
        WheelDatePickerSheet(
            initialDate = selected,
            today = today,
            onConfirm = { date ->
                onDateTextChange(date.toString())
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelDatePickerSheet(
    initialDate: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initialDate) { mutableStateOf(initialDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Text(
                    "选择日期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { onConfirm(draft) }) {
                    Text("确定")
                }
            }
            WheelDatePicker(
                date = draft,
                onDateChange = { draft = it },
                today = today,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WheelDatePicker(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    val minYear = minOf(today.year - 30, date.year)
    val maxYear = maxOf(today.year + 1, date.year)
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }
    val months = remember { (1..12).toList() }
    val daysInMonth = remember(date.year, date.monthValue) {
        (1..YearMonth.of(date.year, date.monthValue).lengthOfMonth()).toList()
    }

    Row(
        modifier = modifier.height(WheelItemHeight * VisibleWheelItems),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            items = years.map { "${it}年" },
            selectedIndex = (date.year - minYear).coerceIn(0, years.lastIndex),
            onSelectedIndexChange = { index ->
                val year = years.getOrElse(index) { date.year }
                onDateChange(clampedLocalDate(year, date.monthValue, date.dayOfMonth))
            },
            modifier = Modifier.weight(1.2f)
        )
        WheelColumn(
            items = months.map { "${it}月" },
            selectedIndex = date.monthValue - 1,
            onSelectedIndexChange = { index ->
                val month = months.getOrElse(index) { date.monthValue }
                onDateChange(clampedLocalDate(date.year, month, date.dayOfMonth))
            },
            modifier = Modifier.weight(1f)
        )
        key(date.year, date.monthValue) {
            WheelColumn(
                items = daysInMonth.map { "${it}日" },
                selectedIndex = (date.dayOfMonth - 1).coerceIn(0, daysInMonth.lastIndex),
                onSelectedIndexChange = { index ->
                    val day = daysInMonth.getOrElse(index) { date.dayOfMonth }
                    onDateChange(clampedLocalDate(date.year, date.monthValue, day))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val latestIndex = rememberUpdatedState(safeIndex)
    val latestCallback = rememberUpdatedState(onSelectedIndexChange)
    val edgePadding = WheelItemHeight * ((VisibleWheelItems - 1) / 2)

    LaunchedEffect(safeIndex, items.size) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != safeIndex) {
            listState.scrollToItem(safeIndex)
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .filter { (inProgress, _) -> !inProgress }
            .map { (_, first) -> first.coerceIn(0, items.lastIndex) }
            .distinctUntilChanged()
            .collect { index ->
                if (index != latestIndex.value) {
                    latestCallback.value(index)
                }
            }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = edgePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight * VisibleWheelItems)
        ) {
            itemsIndexed(items, key = { _, item -> item }) { index, item ->
                val selected = index == safeIndex
                Text(
                    text = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    }
                )
            }
        }
    }
}
