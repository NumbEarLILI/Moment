package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.moment.ui.Routes
import com.example.moment.ui.common.MoodSummaryBadge

@Composable
fun DiaryEditorForm(
    state: DiaryEditorUiState,
    headline: String,
    saveButtonLabel: String,
    placePickDiaryId: Long,
    navController: NavHostController,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (state.isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val expandedDiaryLayout =
                state.plogFragments.isNotEmpty() || state.fragmentStories.isNotEmpty()
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors
            )
            if (state.plogFragments.isNotEmpty()) {
                DiaryPlogTimeline(
                    fragments = state.plogFragments,
                    fragmentStories = state.fragmentStories,
                    locationPins = state.locationPins,
                    onLocationPinClick = { pin ->
                        navController.navigate(
                            Routes.placePick(
                                pin.latitude,
                                pin.longitude,
                                pin.placeName,
                                pin.fragmentId,
                                placePickDiaryId
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (state.fragmentStories.isNotEmpty()) {
                Text(
                    "按时间 · 每一刻",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.fragmentStories.forEach { story ->
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
            OutlinedTextField(
                value = state.body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (expandedDiaryLayout) "整篇文字（可选）" else "正文") },
                supportingText = if (expandedDiaryLayout) {
                    { Text("可选：摘要或全文润色。") }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors,
                minLines = if (expandedDiaryLayout) 4 else 6
            )
            if (state.plogFragments.isEmpty()) {
                DiaryLocationPinsRow(
                    pins = state.locationPins,
                    onPinClick = { pin ->
                        navController.navigate(
                            Routes.placePick(
                                pin.latitude,
                                pin.longitude,
                                pin.placeName,
                                pin.fragmentId,
                                placePickDiaryId
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.plogFragments.isEmpty() && state.imageUris.isNotEmpty()) {
                DiaryImageGallery(imageUris = state.imageUris, modifier = Modifier.fillMaxWidth())
            }
            if (state.highlights.isNotEmpty()) {
                Text(
                    "今日亮点：${state.highlights.joinToString(" / ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.moodSummary?.let {
                MoodSummaryBadge(summary = it, modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.sourceFragmentIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (state.isSaving) "保存中..." else saveButtonLabel)
            }
        }
        }
    }
}
