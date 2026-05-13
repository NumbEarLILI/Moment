package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class RuleBasedDiaryGenerator @Inject constructor(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DiaryGenerator {
    override fun generate(date: LocalDate, fragments: List<LifeFragment>): DiaryDraft {
        val sorted = fragments.sortedBy { it.createdAt }
        if (sorted.isEmpty()) {
            return DiaryDraft(
                title = "还没有记录的一天",
                body = "今天还没有记录。先写下一点生活碎片，再回来生成属于今天的手帐吧。",
                highlights = emptyList(),
                moodSummary = null
            )
        }

        val mainMood = sorted.mapNotNull { it.mood }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Mood, Int>> { it.value }.thenBy { it.key.ordinal })
            ?.key
        val grouped = sorted
            .filter { it.content.isNotBlank() }
            .groupBy { bucketFor(it) }
            .toSortedMap(compareBy { it.order })
        val body = grouped.entries.joinToString(separator = "\n\n") { (bucket, items) ->
            val text = items.joinToString(separator = "；") { it.content.trim() }
            "${bucket.label}，$text"
        }.ifBlank {
            "今天留下了 ${sorted.sumOf { it.imageUris.size }} 张图片碎片，适合稍后补上一些文字。"
        }

        return DiaryDraft(
            title = "${mainMood?.displayName ?: "有记录"}的一天",
            body = body,
            highlights = highlights(sorted),
            moodSummary = mainMood?.let { "今天整体偏${it.displayName}。" },
            sourceFragmentIds = sorted.map { it.id }
        )
    }

    private fun bucketFor(fragment: LifeFragment): TimeBucket {
        val hour = fragment.createdAt.atZone(zoneId).hour
        return when (hour) {
            in 5..9 -> TimeBucket.EARLY_MORNING
            in 10..11 -> TimeBucket.MORNING
            in 12..17 -> TimeBucket.AFTERNOON
            in 18..23 -> TimeBucket.NIGHT
            else -> TimeBucket.LATE_NIGHT
        }
    }

    private fun highlights(fragments: List<LifeFragment>): List<String> =
        fragments
            .asSequence()
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
            .take(3)
            .toList()

    private enum class TimeBucket(val label: String, val order: Int) {
        LATE_NIGHT("深夜", 0),
        EARLY_MORNING("清晨", 1),
        MORNING("上午", 2),
        AFTERNOON("午后", 3),
        NIGHT("夜晚", 4)
    }
}
