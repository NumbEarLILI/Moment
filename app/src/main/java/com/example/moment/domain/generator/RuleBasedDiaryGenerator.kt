package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RuleBasedDiaryGenerator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DiaryGenerator {
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun generate(date: LocalDate, fragments: List<LifeFragment>, priorSavedDiary: DiaryEntry?): DiaryDraft {
        val sorted = fragments.sortedBy { it.createdAt }
        if (sorted.isEmpty()) {
            return DiaryDraft(
                title = "还没有记录的一天",
                body = "今天还没有记录。先写下一点生活碎片，再回来生成属于今天的手帐吧。",
                highlights = emptyList(),
                moodSummary = null
            )
        }

        val priorIds = priorSavedDiary?.sourceFragmentIds?.toSet().orEmpty()
        val newFragments = if (priorIds.isEmpty()) {
            emptyList()
        } else {
            sorted.filter { it.id !in priorIds }
        }
        if (priorSavedDiary != null && newFragments.isNotEmpty()) {
            return mergeOntoPriorDiary(priorSavedDiary, sorted, newFragments)
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

    private fun mergeOntoPriorDiary(
        prior: DiaryEntry,
        sorted: List<LifeFragment>,
        newFragments: List<LifeFragment>
    ): DiaryDraft {
        val newBodyLines = newFragments.mapNotNull { lineForFragment(it) }
        val newSection = newBodyLines.joinToString(separator = "\n\n").ifBlank {
            "新留下了 ${newFragments.sumOf { it.imageUris.size }} 张图片碎片。"
        }
        val mergedBody = prior.body.trimEnd() + "\n\n" + "—— 新增碎片 ——\n\n" + newSection
        val mergedHighlights = (prior.highlights + highlights(newFragments))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .toList()
        val mainMood = sorted.mapNotNull { it.mood }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Mood, Int>> { it.value }.thenBy { it.key.ordinal })
            ?.key
        val mergedMood = mergeMoodLine(prior.moodSummary, mainMood)
        return DiaryDraft(
            title = prior.title.ifBlank { "${mainMood?.displayName ?: "有记录"}的一天" },
            body = mergedBody,
            highlights = mergedHighlights,
            moodSummary = mergedMood,
            sourceFragmentIds = sorted.map { it.id }
        )
    }

    private fun mergeMoodLine(prior: String?, mainMood: Mood?): String? {
        val tail = mainMood?.let { "纳入新碎片后，整体仍偏${it.displayName}。" }
        return when {
            prior.isNullOrBlank() -> tail
            tail == null -> prior
            else -> prior.trimEnd().trimEnd('。') + "；" + tail
        }
    }

    private fun formatClock(fragment: LifeFragment): String =
        fragment.createdAt.atZone(zoneId).toLocalTime().format(clockFormatter)

    private fun lineForFragment(fragment: LifeFragment): String? {
        val time = formatClock(fragment)
        val text = fragment.content.trim()
        val suffix = locationSuffix(fragment).orEmpty()
        return when {
            text.isNotEmpty() -> "$time $text$suffix"
            fragment.imageUris.isNotEmpty() ->
                "$time（${fragment.imageUris.size} 张图片记录）$suffix"
            else -> null
        }
    }

    private fun locationSuffix(fragment: LifeFragment): String? {
        val loc = fragment.location ?: return null
        val label = loc.label?.trim()?.takeIf { it.isNotEmpty() }
            ?: String.format(Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
        return " · 📍$label"
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
