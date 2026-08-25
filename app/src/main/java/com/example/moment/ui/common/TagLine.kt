package com.example.moment.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TagLine(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    val text = tags
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("   ") { "#$it" }
    if (text.isEmpty()) return
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
