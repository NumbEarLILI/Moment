package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentAiStory
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
                moodSummary = null,
                fragmentStories = emptyList()
            )
        }

        val priorIds = priorSavedDiary?.sourceFragmentIds?.toSet().orEmpty()
        // 已保存手帐若未记录 sourceFragmentIds（旧数据或纯手补），仍应将当日碎片视为「新增」并叠在正文后，避免整篇重写丢字。
        val newFragments = when {
            priorSavedDiary == null -> emptyList()
            sorted.isEmpty() -> emptyList()
            priorIds.isEmpty() -> sorted
            else -> sorted.filter { it.id !in priorIds }
        }
        if (priorSavedDiary != null && newFragments.isNotEmpty()) {
            return mergeOntoPriorDiary(priorSavedDiary, sorted, newFragments)
        }

        val mainMood = sorted.mapNotNull { it.mood }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Mood, Int>> { it.value }.thenBy { it.key.ordinal })
            ?.key

        val stories = fragmentStoriesFor(sorted)
        val body = buildShortDaySummary(sorted, mainMood)

        return DiaryDraft(
            title = "${mainMood?.displayName ?: "有记录"}的一天",
            body = body,
            highlights = highlights(sorted),
            moodSummary = mainMood?.let { "今天整体偏${it.displayName}。" },
            sourceFragmentIds = sorted.map { it.id },
            fragmentStories = stories
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
            sourceFragmentIds = sorted.map { it.id },
            fragmentStories = fragmentStoriesFor(sorted)
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

    private fun fragmentStoriesFor(sorted: List<LifeFragment>): List<FragmentAiStory> =
        sorted.mapNotNull { f ->
            val s = storyForFragment(f)
            if (s.isBlank()) null else FragmentAiStory(f.id, s)
        }

    /** 单条时间线主文案：用户原话优先，否则图片提示；不含地点后缀（地点单独展示）。 */
    private fun storyForFragment(fragment: LifeFragment): String {
        val text = fragment.content.trim()
        if (text.isNotEmpty()) return text
        if (fragment.imageUris.isNotEmpty()) return "${fragment.imageUris.size} 张图片记录"
        return ""
    }

    private fun buildShortDaySummary(sorted: List<LifeFragment>, mainMood: Mood?): String {
        val moodPart = mainMood?.let { "整体偏${it.displayName}。" } ?: ""
        val rangePart = if (sorted.size == 1) {
            "今日一则瞬间，细节见下方时间线。"
        } else {
            "今日共 ${sorted.size} 则瞬间，按时间陈列于下。"
        }
        return (moodPart + rangePart).trim()
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
