package com.example.moment.domain.model

import java.time.Instant
import java.time.LocalDate

data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long>,
    val imageUris: List<String> = emptyList(),
    /** 手帐保存的「每条碎片对应图片」，用于 plog 卡片与 NAS 仅恢复日记等场景。 */
    val fragmentImageUris: Map<Long, List<String>> = emptyMap(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val fragmentStories: List<FragmentAiStory> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

/** 手帐时间线锚点：id 列表 ∪ 逐条 story ∪ 分卡图 key（与生成草稿时的合并锚点一致）。 */
fun DiaryEntry.anchoredFragmentIds(): Set<Long> {
    val out = LinkedHashSet<Long>()
    for (id in sourceFragmentIds) if (id > 0L) out.add(id)
    for (s in fragmentStories) if (s.fragmentId > 0L) out.add(s.fragmentId)
    for (id in fragmentImageUris.keys) if (id > 0L) out.add(id)
    return out
}
