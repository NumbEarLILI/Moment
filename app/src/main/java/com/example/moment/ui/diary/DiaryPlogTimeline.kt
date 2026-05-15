package com.example.moment.ui.diary

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moment.domain.location.shortenedDiaryPlaceLabel
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.FullscreenImageViewer
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiaryPlogTimeline(
    fragments: List<LifeFragment>,
    modifier: Modifier = Modifier,
    fragmentStories: List<FragmentAiStory> = emptyList(),
    locationPins: List<DiaryLocationPin> = emptyList(),
    onLocationPinClick: ((DiaryLocationPin) -> Unit)? = null,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    if (fragments.isEmpty()) return
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val storyById = remember(fragmentStories) {
        fragmentStories.associateBy { it.fragmentId }
    }
    var fullscreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    fullscreen?.let { (uris, start) ->
        FullscreenImageViewer(
            imageUris = uris,
            initialPage = start,
            onDismiss = { fullscreen = null }
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "按时间 · 每一刻",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        fragments.forEach { fragment ->
            val pin = locationPins.firstOrNull { it.fragmentId == fragment.id }
            DiaryPlogMomentCard(
                fragment = fragment,
                zoneId = zoneId,
                timeFormatter = timeFmt,
                storyText = storyById[fragment.id]?.text?.trim().orEmpty(),
                locationPin = pin,
                onLocationPinClick = onLocationPinClick,
                onImageClick = { uris, index -> fullscreen = uris to index }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryPlogMomentCard(
    fragment: LifeFragment,
    zoneId: ZoneId,
    timeFormatter: DateTimeFormatter,
    storyText: String,
    locationPin: DiaryLocationPin?,
    onLocationPinClick: ((DiaryLocationPin) -> Unit)?,
    onImageClick: (List<String>, Int) -> Unit
) {
    val time = remember(fragment.id, fragment.createdAt, zoneId) {
        fragment.createdAt.atZone(zoneId).toLocalTime().format(timeFormatter)
    }
    val rawContent = fragment.content.trim()
    val uris = fragment.imageUris.map { it.trim() }.filter { it.isNotEmpty() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                fragment.mood?.let { mood ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            mood.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (uris.isNotEmpty()) {
                AsyncImage(
                    model = uris.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onImageClick(uris, 0) }
                )
                if (uris.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(uris.drop(1), key = { idx, uri -> "${fragment.id}:$idx:$uri" }) { idx, uri ->
                            val globalIndex = idx + 1
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(uris, globalIndex) }
                            )
                        }
                    }
                }
            }

            when {
                storyText.isNotEmpty() -> {
                    Text(
                        storyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                rawContent.isNotEmpty() -> {
                    Text(
                        rawContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                uris.isNotEmpty() -> {
                    Text(
                        "这一则只有图片记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            when {
                locationPin != null && onLocationPinClick != null -> {
                    AssistChip(
                        onClick = { onLocationPinClick(locationPin) },
                        label = {
                            Text(
                                "地点 · ${shortenedDiaryPlaceLabel(locationPin.placeName)}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                else -> {
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

            if (fragment.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    fragment.tags.forEach { tag ->
                        val t = tag.trim()
                        if (t.isNotEmpty()) {
                            Text(
                                "#$t",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
