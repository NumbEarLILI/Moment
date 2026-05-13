package com.example.moment.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.moment.domain.model.DiaryLocationPin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryLocationPinsRow(
    pins: List<DiaryLocationPin>,
    onPinClick: (DiaryLocationPin) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pins.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "地点",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pins.forEach { pin ->
                AssistChip(
                    onClick = { onPinClick(pin) },
                    label = { Text(pin.placeName) }
                )
            }
        }
    }
}
