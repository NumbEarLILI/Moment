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

    /** Wi-Fi 直连传原图的上限；再大就按照片质量压缩，而不是头像缩略图。 */
    const val WIFI_MAX_IMAGE_BYTES = 8 * 1024 * 1024
    const val WIFI_MAX_IMAGE_EDGE_PX = 2048
    const val WIFI_JPEG_QUALITY = 88

    /** 蓝牙链路太窄，碎片只发文字卡片；Wi-Fi Direct 带上原图。 */
    fun includeImage(transport: NearbyTransport): Boolean =
        transport == NearbyTransport.WifiDirect

    /** 自己气泡里的预览路径：蓝牙不带图，Wi-Fi 仅在确实附上了 JPEG 时才显示本地原图。 */
    fun localPreviewPath(
        transport: NearbyTransport,
        localPath: String,
        attachedJpeg: ByteArray
    ): String {
        if (!includeImage(transport) || attachedJpeg.isEmpty()) return ""
        return localPath
    }
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
