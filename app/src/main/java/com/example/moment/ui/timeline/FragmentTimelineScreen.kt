package com.example.moment.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.FullscreenImageViewer
import com.example.moment.ui.common.MoodBadge
import com.example.moment.ui.common.QuietTextAction
import com.example.moment.ui.common.TagLine
import com.example.moment.ui.theme.MomentHairline
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_INLINE_TIMELINE_LIMIT = 30

@Composable
fun FragmentTimelineSection(
    state: FragmentTimelineUiState,
    onAddFragment: () -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    onDeleteFragment: (Long) -> Unit,
    onClearDeleteError: () -> Unit,
    hiddenFragmentId: Long? = null,
    hiddenDiaryFallbackDate: LocalDate? = null,
    initialItemLimit: Int = DEFAULT_INLINE_TIMELINE_LIMIT,
    modifier: Modifier = Modifier
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val timestampFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA) }
    val diaryDateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA) }
    var fullscreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var pendingDelete by remember { mutableStateOf<LifeFragment?>(null) }
    var showAllItems by remember { mutableStateOf(false) }
    val displayItems = remember(state.items, hiddenFragmentId, hiddenDiaryFallbackDate) {
        state.items.filterNot { item ->
            when (item) {
                is FragmentTimelineItem.Fragment -> item.fragment.id == hiddenFragmentId
                is FragmentTimelineItem.DiaryFallback -> item.diary.date == hiddenDiaryFallbackDate
            }
        }
    }
    val safeLimit = initialItemLimit.coerceAtLeast(1)
    val visibleItems = remember(displayItems, showAllItems, safeLimit) {
        if (showAllItems) displayItems else displayItems.take(safeLimit)
    }

    fullscreen?.let { (uris, start) ->
        FullscreenImageViewer(
            imageUris = uris,
            initialPage = start,
            onDismiss = { fullscreen = null }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        state.deleteErrorMessage?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onClearDeleteError) {
                    Text("知道了")
                }
            }
        }

        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(vertical = 12.dp))
            state.errorMessage != null -> {
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            displayItems.isEmpty() -> EmptyTimelineHint(onAddFragment = onAddFragment)
            else -> {
                visibleItems.forEachIndexed { index, item ->
                    key(timelineItemKey(item)) {
                        if (index > 0) {
                            MomentHairline(Modifier.padding(vertical = 14.dp))
                        }
                        TimelineItemCard(
                            item = item,
                            zoneId = zoneId,
                            timestampFormatter = timestampFormatter,
                            diaryDateFormatter = diaryDateFormatter,
                            deletingFragmentId = state.deletingFragmentId,
                            onContinueEditFragment = onContinueEditFragment,
                            onOpenDiary = onOpenDiary,
                            onDeleteRequest = { pendingDelete = it },
                            onImageClick = { uris, imageIndex -> fullscreen = uris to imageIndex }
                        )
                    }
                }
                if (displayItems.size > safeLimit) {
                    TextButton(
                        onClick = { showAllItems = !showAllItems },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (showAllItems) {
                                "收起"
                            } else {
                                "显示全部 ${displayItems.size} 条"
                            }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { fragment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条碎片？") },
            text = {
                Text(
                    "删除后将无法从本机恢复。确认删除「${fragment.content.trim().take(24).ifBlank { "无文字碎片" }}」吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteFragment(fragment.id)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TimelineItemCard(
    item: FragmentTimelineItem,
    zoneId: ZoneId,
    timestampFormatter: DateTimeFormatter,
    diaryDateFormatter: DateTimeFormatter,
    deletingFragmentId: Long?,
    onContinueEditFragment: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    onDeleteRequest: (LifeFragment) -> Unit,
    onImageClick: (List<String>, Int) -> Unit
) {
    when (item) {
        is FragmentTimelineItem.Fragment -> {
            val fragment = item.fragment
            FragmentTimelineCard(
                fragment = fragment,
                zoneId = zoneId,
                timestampFormatter = timestampFormatter,
                onContinueEdit = { onContinueEditFragment(fragment.id) },
                isDeleting = deletingFragmentId == fragment.id,
                onDeleteRequest = { onDeleteRequest(fragment) },
                onImageClick = onImageClick
            )
        }
        is FragmentTimelineItem.DiaryFallback -> {
            DiaryFallbackTimelineCard(
                diary = item.diary,
                dateFormatter = diaryDateFormatter,
                onOpenDiary = { onOpenDiary(item.diary.id) },
                onImageClick = onImageClick
            )
        }
    }
}

private fun timelineItemKey(item: FragmentTimelineItem): String =
    when (item) {
        is FragmentTimelineItem.Fragment -> "fragment-${item.fragment.id}-${item.fragment.stableId}"
        is FragmentTimelineItem.DiaryFallback -> "diary-${item.diary.id}-${item.diary.date}"
    }

@Composable
private fun EmptyTimelineHint(onAddFragment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "写下这一刻，它会出现在这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAddFragment) {
            Text("开始记录")
        }
    }
}

@Composable
private fun FragmentTimelineCard(
    fragment: LifeFragment,
    zoneId: ZoneId,
    timestampFormatter: DateTimeFormatter,
    onContinueEdit: () -> Unit,
    isDeleting: Boolean,
    onDeleteRequest: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit
) {
    val timestamp = remember(fragment.createdAt, zoneId, timestampFormatter) {
        fragment.createdAt.atZone(zoneId).format(timestampFormatter)
    }
    val imageUris = remember(fragment.imageUris) {
        fragment.imageUris.map { it.trim() }.filter { it.isNotEmpty() }
    }
    val content = fragment.content.trim()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timestamp,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            QuietTextAction(
                text = "编辑",
                onClick = onContinueEdit,
                enabled = !isDeleting
            )
            QuietTextAction(
                text = if (isDeleting) "删除中..." else "删除",
                onClick = onDeleteRequest,
                enabled = !isDeleting,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (imageUris.isNotEmpty()) {
            TimelineImageGallery(
                keyPrefix = "fragment-${fragment.id}",
                imageUris = imageUris,
                onImageClick = onImageClick
            )
        }

        when {
            content.isNotEmpty() -> {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            imageUris.isNotEmpty() -> {
                Text(
                    "图片记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        fragment.mood?.let { mood ->
            MoodBadge(mood = mood)
        }
        TagLine(tags = fragment.tags)
        fragment.location?.let { loc ->
            val label = loc.label?.trim()?.takeIf { it.isNotEmpty() }
                ?: String.format(Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiaryFallbackTimelineCard(
    diary: DiaryEntry,
    dateFormatter: DateTimeFormatter,
    onOpenDiary: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit
) {
    val imageUris = remember(diary.imageUris) {
        diary.imageUris.map { it.trim() }.filter { it.isNotEmpty() }
    }
    val title = diary.title.trim()
    val body = diary.body.trim()
    val highlights = remember(diary.highlights) {
        diary.highlights.map { it.trim() }.filter { it.isNotEmpty() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = diary.date.format(dateFormatter),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            QuietTextAction(
                text = "查看手帐",
                onClick = onOpenDiary,
                enabled = diary.id > 0
            )
        }

        if (imageUris.isNotEmpty()) {
            TimelineImageGallery(
                keyPrefix = "diary-${diary.id}",
                imageUris = imageUris,
                onImageClick = onImageClick
            )
        }

        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (body.isNotEmpty()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
        diary.moodSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { mood ->
            Text(
                text = "心情 · $mood",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (highlights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                highlights.forEach { highlight ->
                    Text(
                        text = highlight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineImageGallery(
    keyPrefix: String,
    imageUris: List<String>,
    onImageClick: (List<String>, Int) -> Unit
) {
    val heroHeight = if (imageUris.size == 1) 300.dp else 220.dp
    AsyncImage(
        model = imageUris.first(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(MaterialTheme.shapes.large)
            .clickable { onImageClick(imageUris, 0) }
    )
    if (imageUris.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(imageUris.drop(1), key = { idx, uri -> "$keyPrefix:$idx:$uri" }) { idx, uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(imageUris, idx + 1) }
                )
            }
        }
    }
}
