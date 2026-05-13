package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RuleBasedDiaryGenerator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DiaryGenerator {
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

        val bodyLines = sorted.mapNotNull { lineForFragment(it) }
        val body = bodyLines.joinToString(separator = "\n\n").ifBlank {
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

    private fun formatClock(fragment: LifeFragment): String =
        fragment.createdAt.atZone(zoneId).toLocalTime().format(clockFormatter)

    private fun lineForFragment(fragment: LifeFragment): String? {
        val time = formatClock(fragment)
        val text = fragment.content.trim()
        return when {
            text.isNotEmpty() -> "$time $text"
            fragment.imageUris.isNotEmpty() ->
                "$time（${fragment.imageUris.size} 张图片记录）"
            else -> null
        }
    }

    private fun highlights(fragments: List<LifeFragment>): List<String> =
        fragments
            .asSequence()
            .filter { it.content.isNotBlank() }
            .sortedWith(
                compareByDescending<LifeFragment> { it.tags.isNotEmpty() }
                    .thenByDescending { it.content.trim().length }
                    .thenBy { it.content.trim() }
            )
            .take(3)
            .map { "${formatClock(it)} ${it.content.trim()}" }
            .toList()
}
