package com.example.moment.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.moment.domain.model.Mood

@Composable
fun MoodBadge(
    mood: Mood,
    modifier: Modifier = Modifier,
    label: String = "心情"
) {
    Text(
        text = "$label · ${mood.displayName}",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun MoodSummaryBadge(
    summary: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "心情 · $summary",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
