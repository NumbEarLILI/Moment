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
    /** 手帐时间线顺序：引用 [LifeFragment.stableId]，非 Room 自增 id。 */
    val sourceFragmentStableIds: List<String>,
    val imageUris: List<String> = emptyList(),
    /** 手帐保存的「每条碎片对应图片」，用于 plog 卡片与 NAS 仅恢复日记等场景。 */
    val fragmentImageUris: Map<String, List<String>> = emptyMap(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val fragmentStories: List<FragmentAiStory> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

/** 手帐时间线锚点：稳定 id 列表 ∪ 逐条 story ∪ 分卡图 key（与生成草稿时的合并锚点一致）。 */
fun DiaryEntry.anchoredFragmentIds(): Set<String> {
    val out = LinkedHashSet<String>()
    for (id in sourceFragmentStableIds) if (id.isNotBlank()) out.add(id)
    for (s in fragmentStories) if (s.fragmentStableId.isNotBlank()) out.add(s.fragmentStableId)
    for (id in fragmentImageUris.keys) if (id.isNotBlank()) out.add(id)
    return out
}
