package com.example.moment.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        state.deleteErrorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onClearDeleteError) {
                        Text("知道了")
                    }
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
                visibleItems.forEach { item ->
                    key(timelineItemKey(item)) {
                        TimelineItemCard(
                            item = item,
                            zoneId = zoneId,
                            timestampFormatter = timestampFormatter,
                            diaryDateFormatter = diaryDateFormatter,
                            deletingFragmentId = state.deletingFragmentId,
                            onContinueEditFragment = onContinueEditFragment,
                            onOpenDiary = onOpenDiary,
                            onDeleteRequest = { pendingDelete = it },
                            onImageClick = { uris, index -> fullscreen = uris to index }
                        )
                    }
                }
                if (displayItems.size > safeLimit) {
                    OutlinedButton(
                        onClick = { showAllItems = !showAllItems },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            if (showAllItems) {
                                "收起到最近 $safeLimit 条"
                            } else {
                                "显示全部 ${displayItems.size} 条内容"
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("还没有碎片", style = MaterialTheme.typography.titleMedium)
            Text(
                "记录一条文字、照片或地点，它会出现在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f)
            )
            Button(onClick = onAddFragment, shape = MaterialTheme.shapes.large) {
                Text("开始记录")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "生活碎片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onContinueEdit,
                    enabled = !isDeleting,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("继续编辑")
                }
                TextButton(
                    onClick = onDeleteRequest,
                    enabled = !isDeleting,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (isDeleting) "删除中..." else "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
                        "这一条只有图片记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            fragment.mood?.let { mood ->
                MoodBadge(mood = mood, label = "心情")
            }

            if (fragment.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fragment.tags.forEach { tag ->
                        val text = tag.trim()
                        if (text.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "#$text",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            fragment.location?.let { loc ->
                val label = loc.label?.trim()?.takeIf { it.isNotEmpty() }
                    ?: String.format(Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
                Text(
                    "地点 · $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = diary.date.format(dateFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "手帐 · 无碎片记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onOpenDiary,
                    enabled = diary.id > 0,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("查看手帐")
                }
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
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = "心情 · $mood",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    highlights.forEach { highlight ->
                        Text(
                            text = "· $highlight",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            .clip(MaterialTheme.shapes.medium)
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
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(imageUris, idx + 1) }
                )
            }
        }
    }
}
