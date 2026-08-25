package com.example.moment.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moment.domain.location.shortenedDiaryPlaceLabel
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.ui.common.FragmentPlaceLine
import com.example.moment.ui.common.FullscreenImageViewer
import com.example.moment.ui.common.TagLine
import com.example.moment.ui.theme.MomentHairline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun mergedPlogDisplayImageUris(
    fragment: LifeFragment,
    diaryFragmentUris: Map<String, List<String>>
): List<String> {
    val fromFrag = fragment.imageUris.map { it.trim() }.filter { it.isNotEmpty() }
    val fromDiary = diaryFragmentUris[fragment.stableId].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    if (fromDiary.isEmpty()) return fromFrag
    if (fromFrag.isEmpty()) return fromDiary
    val seen = LinkedHashSet<String>()
    val out = ArrayList<String>()
    for (u in fromFrag) if (seen.add(u)) out.add(u)
    for (u in fromDiary) if (seen.add(u)) out.add(u)
    return out
}

@Composable
fun DiaryPlogTimeline(
    fragments: List<LifeFragment>,
    modifier: Modifier = Modifier,
    fragmentStories: List<FragmentAiStory> = emptyList(),
    fragmentImageUris: Map<String, List<String>> = emptyMap(),
    locationPins: List<DiaryLocationPin> = emptyList(),
    onLocationPinClick: ((DiaryLocationPin) -> Unit)? = null,
    plogTimeTexts: Map<String, String> = emptyMap(),
    onPlogTimeChange: ((String, String) -> Unit)? = null,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    if (fragments.isEmpty()) return
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val storyByStableId = remember(fragmentStories) {
        fragmentStories.associateBy { it.fragmentStableId }
    }
    var fullscreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    fullscreen?.let { (uris, start) ->
        FullscreenImageViewer(
            imageUris = uris,
            initialPage = start,
            onDismiss = { fullscreen = null }
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        fragments.forEachIndexed { index, fragment ->
            val pin = locationPins.firstOrNull { it.fragmentStableId == fragment.stableId }
            if (index > 0) {
                MomentHairline(Modifier.padding(vertical = 14.dp))
            }
            DiaryPlogMomentCard(
                fragment = fragment,
                displayImageUris = mergedPlogDisplayImageUris(fragment, fragmentImageUris),
                zoneId = zoneId,
                timeFormatter = timeFmt,
                storyText = storyByStableId[fragment.stableId]?.text?.trim().orEmpty(),
                locationPin = pin,
                onLocationPinClick = onLocationPinClick,
                editableTimeText = plogTimeTexts[fragment.stableId],
                onPlogTimeChange = onPlogTimeChange,
                onImageClick = { uris, index -> fullscreen = uris to index }
            )
        }
    }
}

@Composable
private fun DiaryPlogMomentCard(
    fragment: LifeFragment,
    displayImageUris: List<String>,
    zoneId: ZoneId,
    timeFormatter: DateTimeFormatter,
    storyText: String,
    locationPin: DiaryLocationPin?,
    onLocationPinClick: ((DiaryLocationPin) -> Unit)?,
    editableTimeText: String?,
    onPlogTimeChange: ((String, String) -> Unit)?,
    onImageClick: (List<String>, Int) -> Unit
) {
    val time = remember(fragment.stableId, fragment.createdAt, zoneId) {
        fragment.createdAt.atZone(zoneId).toLocalTime().format(timeFormatter)
    }
    val rawContent = fragment.content.trim()
    val uris = displayImageUris

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onPlogTimeChange != null) {
                    OutlinedTextField(
                        value = editableTimeText ?: time,
                        onValueChange = { onPlogTimeChange(fragment.stableId, it) },
                        modifier = Modifier.width(92.dp),
                        label = { Text("时间") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                fragment.mood?.let { mood ->
                    Text(
                        mood.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uris.isNotEmpty()) {
                val heroHeight = if (uris.size == 1) 320.dp else 220.dp
                AsyncImage(
                    model = uris.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
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

            fragment.weather?.caption()?.let { weatherLine ->
                Text(
                    weatherLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                locationPin != null && onLocationPinClick != null -> {
                    FragmentPlaceLine(
                        location = FragmentLocation(
                            latitude = locationPin.latitude,
                            longitude = locationPin.longitude,
                            label = locationPin.placeName
                        ),
                        placeLabel = shortenedDiaryPlaceLabel(locationPin.placeName),
                        onClick = { onLocationPinClick(locationPin) }
                    )
                }
                else -> {
                    fragment.location?.let { loc ->
                        FragmentPlaceLine(location = loc)
                    }
                }
            }

            TagLine(tags = fragment.tags)
        }
    }

/**
 * 按手帐保存的 stableId 顺序构建时间线；本地库里不存在的 id（例如 NAS 只恢复了日记）
 * 用占位 [LifeFragment]，正文由 [DiaryPlogTimeline] 从 [FragmentAiStory] 读取。
 */
fun lifeFragmentsForPlogTimeline(
    orderedStableIds: List<String>,
    loadedFragments: List<LifeFragment>,
    fragmentCreatedAtEpochMillis: Map<String, Long> = emptyMap()
): List<LifeFragment> {
    if (orderedStableIds.isEmpty()) return emptyList()
    val byStable = loadedFragments.associateBy { it.stableId }
    val archivedCreatedAt = fragmentCreatedAtEpochMillis.mapNotNull { (sid, epochMillis) ->
        val key = sid.trim()
        if (key.isEmpty()) null else key to Instant.ofEpochMilli(epochMillis)
    }.toMap()
    val seen = linkedSetOf<String>()
    var placeholderSeq = 0
    return orderedStableIds.mapNotNull { sid ->
        val key = sid.trim()
        if (key.isEmpty() || !seen.add(key)) return@mapNotNull null
        val archivedTime = archivedCreatedAt[key]
        byStable[key]?.let { fragment ->
            if (archivedTime != null && fragment.createdAt != archivedTime) {
                fragment.copy(createdAt = archivedTime)
            } else {
                fragment
            }
        } ?: run {
            val t = archivedTime ?: placeholderInstantForNasOnlyRow(placeholderSeq++)
            LifeFragment(
                id = 0L,
                stableId = key,
                content = "",
                imageUris = emptyList(),
                mood = null,
                tags = emptyList(),
                createdAt = t,
                updatedAt = t
            )
        }
    }
}

private fun placeholderInstantForNasOnlyRow(sequenceIndex: Int): Instant =
    Instant.ofEpochSecond(1_700_000_000L + sequenceIndex * 60L)
