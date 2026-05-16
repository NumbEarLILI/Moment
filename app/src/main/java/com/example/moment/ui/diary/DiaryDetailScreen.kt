package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.moment.ui.Routes
import com.example.moment.ui.common.MoodSummaryBadge
import com.example.moment.ui.theme.appScaffoldContainerColor

@Composable
fun DiaryDetailScreen(
    navController: NavHostController,
    diaryId: Long,
    onBack: () -> Unit,
    onEditDiary: (Long) -> Unit,
    viewModel: DiaryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmation,
            title = { Text("删除这篇手帐？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmation) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        containerColor = appScaffoldContainerColor()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack, shape = MaterialTheme.shapes.small) {
                Text("返回", color = MaterialTheme.colorScheme.primary)
            }
            when {
                state.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                state.errorMessage != null -> Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                state.entry == null -> Text(
                    "没有找到这篇日记。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    val entry = checkNotNull(state.entry)
                    val hasPlog = state.plogFragments.isNotEmpty()
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        entry.date.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { navController.navigate("preview/${entry.date}") }
                        ) {
                            Text("合并新碎片", color = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onEditDiary(diaryId) }) {
                                Text("编辑", color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = viewModel::requestDeleteConfirmation) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (hasPlog) {
                        DiaryPlogTimeline(
                            fragments = state.plogFragments,
                            fragmentStories = entry.fragmentStories,
                            fragmentImageUris = entry.fragmentImageUris,
                            locationPins = entry.locationPins,
                            onLocationPinClick = { pin ->
                                navController.navigate(
                                    Routes.placePick(
                                        pin.latitude,
                                        pin.longitude,
                                        pin.placeName,
                                        pin.fragmentId,
                                        diaryId
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        if (entry.fragmentStories.isNotEmpty()) {
                            Text(
                                "按时间 · 每一刻",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                entry.fragmentStories.forEach { story ->
                                    val t = story.text.trim()
                                    if (t.isNotEmpty()) {
                                        Text(
                                            t,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        when {
                            entry.body.isNotBlank() -> {
                                if (entry.fragmentStories.isNotEmpty()) {
                                    Text(
                                        "整篇文字",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    entry.body,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            entry.fragmentStories.isEmpty() -> {
                                Text(
                                    entry.body,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (hasPlog && entry.body.isNotBlank()) {
                        Text(
                            "整篇文字",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!hasPlog) {
                        DiaryLocationPinsRow(
                            pins = entry.locationPins,
                            onPinClick = { pin ->
                                navController.navigate(
                                    Routes.placePick(
                                        pin.latitude,
                                        pin.longitude,
                                        pin.placeName,
                                        pin.fragmentId,
                                        diaryId
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (entry.imageUris.isNotEmpty()) {
                        DiaryImageGallery(imageUris = entry.imageUris, modifier = Modifier.fillMaxWidth())
                    }
                    if (entry.highlights.isNotEmpty()) {
                        Text(
                            "亮点：${entry.highlights.joinToString(" / ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    entry.moodSummary?.let { MoodSummaryBadge(summary = it, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
