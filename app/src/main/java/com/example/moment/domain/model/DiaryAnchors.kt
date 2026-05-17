package com.example.moment.domain.model

/**
 * 与 [DiaryEntry.anchoredFragmentIds] 相同的 id 集合，但 **保留手帐时间线合理顺序**
 *（先 [sourceFragmentStableIds]，再 story、fragmentImageUris 的键中未见者）。
 */
fun orderedAnchoredFragmentIdsForDiary(entry: DiaryEntry): List<String> {
    val seen = linkedSetOf<String>()
    val out = ArrayList<String>()
    fun add(sid: String) {
        val t = sid.trim()
        if (t.isEmpty() || !seen.add(t)) return
        out.add(t)
    }
    for (id in entry.sourceFragmentStableIds) add(id)
    for (s in entry.fragmentStories) add(s.fragmentStableId)
    for (id in entry.fragmentImageUris.keys) add(id)
    return out
}
