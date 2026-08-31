package com.example.moment.domain.nearby

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.fragmentPlaceLabel
import kotlinx.serialization.Serializable

/** 聊天里分享的一条碎片卡片。图片另外走 JPEG，不进这个结构。 */
@Serializable
data class SharedFragmentCard(
    val stableId: String,
    val content: String,
    val mood: String = "",
    val tags: List<String> = emptyList(),
    val place: String = "",
    val weather: String = "",
    val createdAtEpochMillis: Long
) {
    fun contextLine(): String =
        listOfNotNull(
            mood.takeIf { it.isNotBlank() },
            weather.takeIf { it.isNotBlank() },
            place.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
}

object NearbyFragmentSharePolicy {
    const val MAX_CONTENT_CHARS = 500
}

fun LifeFragment.toSharedFragmentCard(): SharedFragmentCard {
    val trimmed = content.trim()
    val clipped = if (trimmed.length > NearbyFragmentSharePolicy.MAX_CONTENT_CHARS) {
        trimmed.take(NearbyFragmentSharePolicy.MAX_CONTENT_CHARS)
    } else {
        trimmed
    }
    return SharedFragmentCard(
        stableId = stableId,
        content = clipped,
        mood = mood?.displayName.orEmpty(),
        tags = tags,
        place = location?.let(::fragmentPlaceLabel).orEmpty(),
        weather = weather?.caption().orEmpty(),
        createdAtEpochMillis = createdAt.toEpochMilli()
    )
}

fun LifeFragment.shareCaption(): String {
    val card = toSharedFragmentCard()
    return card.content.ifBlank { card.contextLine().ifBlank { "分享了一条碎片" } }
}
