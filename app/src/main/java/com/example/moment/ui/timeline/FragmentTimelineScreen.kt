package com.example.moment.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.FullscreenImageViewer
import com.example.moment.ui.common.MoodBadge
import com.example.moment.ui.theme.appScaffoldContainerColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FragmentTimelineScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFragment: () -> Unit,
    onContinueEditFragment: (Long) -> Unit,
    viewModel: FragmentTimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zoneId = remember { ZoneId.systemDefault() }
    val timestampFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA) }
    var fullscreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var pendingDelete by remember { mutableStateOf<LifeFragment?>(null) }

    fullscreen?.let { (uris, start) ->
        FullscreenImageViewer(
            imageUris = uris,
            initialPage = start,
            onDismiss = { fullscreen = null }
        )
    }

    Scaffold(
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TimelineHeader(
                    count = state.fragments.size,
                    onBack = onBack,
                    onOpenSettings = onOpenSettings,
                    onAddFragment = onAddFragment
                )
            }
            state.deleteErrorMessage?.let { message ->
                item {
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
                            TextButton(onClick = viewModel::clearDeleteError) {
                                Text("知道了")
                            }
                        }
                    }
                }
            }

            when {
                state.isLoading -> item {
                    CircularProgressIndicator(Modifier.padding(vertical = 12.dp))
                }
                state.errorMessage != null -> item {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.fragments.isEmpty() -> item {
                    EmptyTimelineHint(onAddFragment = onAddFragment)
                }
                else -> items(state.fragments, key = { "fragment-${it.id}-${it.stableId}" }) { fragment ->
                    FragmentTimelineCard(
                        fragment = fragment,
                        zoneId = zoneId,
                        timestampFormatter = timestampFormatter,
                        onContinueEdit = { onContinueEditFragment(fragment.id) },
                        isDeleting = state.deletingFragmentId == fragment.id,
                        onDeleteRequest = { pendingDelete = fragment },
                        onImageClick = { uris, index -> fullscreen = uris to index }
                    )
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
                        viewModel.delete(fragment.id)
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
private fun TimelineHeader(
    count: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFragment: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, shape = MaterialTheme.shapes.small) {
                    Text("返回", color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenSettings, shape = MaterialTheme.shapes.small) {
                        Text("设置", color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(onClick = onAddFragment, shape = MaterialTheme.shapes.medium) {
                        Text("写新碎片")
                    }
                }
            }
            Text(
                "碎片时间线",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (count > 0) {
                    "最新记录在最上方，共 $count 条碎片。"
                } else {
                    "像刷时间线一样回看每一个生活碎片。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
            )
        }
    }
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
                    fragment = fragment,
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
private fun TimelineImageGallery(
    fragment: LifeFragment,
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
            itemsIndexed(imageUris.drop(1), key = { idx, uri -> "${fragment.id}:$idx:$uri" }) { idx, uri ->
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
