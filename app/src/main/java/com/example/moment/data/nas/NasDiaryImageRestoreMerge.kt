package com.example.moment.data.nas

/**
 * 将 NAS [imageRelativePaths] 槽位解析出的 [resolvedByIndex] 合并进各碎片的展示用 URI 列表。
 * 先用 [fragmentImageIndices] 占槽；未占用的槽再按手帐时间线顺序轮询分配，避免 JSON 残缺时只显示一张图。
 */
internal fun mergeRestoredFragmentBuckets(
    sourceStableIds: List<String>,
    fragmentImageIndices: Map<String, List<Int>>,
    resolvedByIndex: Array<String?>
): Map<String, List<String>> {
    val seenStable = LinkedHashSet<String>()
    val stableOrdered = ArrayList<String>()
    for (s in sourceStableIds) {
        val t = s.trim()
        if (t.isNotEmpty() && seenStable.add(t)) stableOrdered.add(t)
    }
    if (stableOrdered.isEmpty()) return emptyMap()

    val indicesBySid = LinkedHashMap<String, ArrayList<Int>>()
    for ((rawKey, list) in fragmentImageIndices) {
        val sid = rawKey.trim()
        if (sid.isEmpty()) continue
        val merged = indicesBySid.getOrPut(sid) { ArrayList() }
        for (i in list) if (!merged.contains(i)) merged.add(i)
    }

    val buckets = LinkedHashMap<String, ArrayList<String>>()
    for (sid in stableOrdered) {
        buckets[sid] = ArrayList()
    }
    val assignedSlot = mutableSetOf<Int>()

    for (sid in stableOrdered) {
        val indices = indicesBySid[sid] ?: continue
        for (i in indices) {
            if (i !in resolvedByIndex.indices || i in assignedSlot) continue
            val uri = resolvedByIndex[i]?.takeIf { it.isNotBlank() } ?: continue
            buckets[sid]?.add(uri)
            assignedSlot.add(i)
        }
    }

    val n = stableOrdered.size
    for (idx in resolvedByIndex.indices) {
        if (idx in assignedSlot) continue
        val uri = resolvedByIndex[idx]?.takeIf { it.isNotBlank() } ?: continue
        val sid = stableOrdered[idx % n]
        buckets[sid]?.add(uri)
        assignedSlot.add(idx)
    }

    return buckets.filterValues { it.isNotEmpty() }
}
