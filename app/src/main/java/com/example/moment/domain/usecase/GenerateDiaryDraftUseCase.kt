package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.toLlmConnectionConfig
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import javax.inject.Inject

class GenerateDiaryDraftUseCase @Inject constructor(
    private val fragmentRepository: FragmentRepository,
    private val diaryRepository: DiaryRepository,
    private val diaryGenerator: DiaryGenerator,
    private val userPreferencesAccessor: UserPreferencesAccessor,
    private val aiDiaryDraftGenerator: AiDiaryDraftGenerator
) {
    suspend operator fun invoke(
        date: LocalDate,
        mode: DiaryGenerationMode = DiaryGenerationMode.AUTO
    ): DiaryDraft {
        val priorSaved = diaryRepository.getDiaryForDate(date)
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val fragmentImageUris = sorted.flatMap { it.imageUris }
        val mergedImageUris = mergeDiaryImageUris(fragmentImageUris, priorSaved)
        val pins = pinsFromFragments(sorted)

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted, priorSaved)
            return emptyDraft.copy(imageUris = mergedImageUris, locationPins = pins)
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(date, sorted, mergedImageUris, pins, priorSaved)
        }

        val prefs = userPreferencesAccessor.current()
        val config = prefs.toLlmConnectionConfig()
        if (config == null) {
            return finalizeWithRuleGenerator(date, sorted, mergedImageUris, pins, priorSaved)
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config, priorSaved).getOrElse { throw it }
        val filledStories = normalizeAiFragmentStories(sorted, aiDraft.fragmentStories, priorSaved)
        val mergedDraft = mergeAiDraftWithPriorIfNeeded(priorSaved, sorted, aiDraft)
        return mergedDraft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = mergedImageUris,
            locationPins = pins,
            fragmentStories = filledStories
        )
    }

    /**
     * 有已保存手帐时：
     * - **相对底稿有新增碎片**：底稿叙述 + 模型正文叠放，防止模型单段覆盖旧稿。
     * - **无新增碎片**（例如再次打开预览、或 id 列表已齐全）：**只保留底稿叙述**，不把模型全文再拼在后面，避免每次生成都多叠一层。
     */
    private fun mergeAiDraftWithPriorIfNeeded(
        prior: DiaryEntry?,
        sorted: List<LifeFragment>,
        ai: DiaryDraft
    ): DiaryDraft {
        if (prior == null) return ai
        val newFrags = newFragmentsRelativeToPrior(prior, sorted)
        val pb = effectivePriorNarrative(prior).trim()
        val ab = ai.body.trim()

        if (newFrags.isEmpty()) {
            if (pb.isEmpty()) return ai
            val mergedHighlights = (prior.highlights + ai.highlights).asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)
                .toList()
            return ai.copy(
                title = ai.title.trim().ifBlank { prior.title },
                body = pb,
                highlights = mergedHighlights,
                moodSummary = prior.moodSummary?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ai.moodSummary?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

        val body = when {
            pb.isEmpty() -> ab
            ab.isEmpty() -> pb
            else -> pb + "\n\n" + ab
        }
        val title = ai.title.trim().ifBlank { prior.title }
        val highlights = (prior.highlights + ai.highlights).asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .toList()
        val mood = ai.moodSummary?.trim()?.takeIf { it.isNotEmpty() } ?: prior.moodSummary
        return ai.copy(title = title, body = body, highlights = highlights, moodSummary = mood)
    }

    private fun newFragmentsRelativeToPrior(prior: DiaryEntry, sorted: List<LifeFragment>): List<LifeFragment> {
        val priorIds = prior.sourceFragmentIds.toSet()
        return when {
            sorted.isEmpty() -> emptyList()
            priorIds.isEmpty() -> sorted
            else -> sorted.filter { it.id !in priorIds }
        }
    }

    /**
     * 与 RuleBasedDiaryGenerator 一致：plog 手帐常见「正文仅短总述、长文在 fragmentStories」，
     * 若只取 body 会在增量生成时丢掉旧稿可见内容。
     */
    private fun effectivePriorNarrative(prior: DiaryEntry): String {
        val body = prior.body.trim()
        val fromStories = prior.fragmentStories
            .asSequence()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        return when {
            body.isNotEmpty() && fromStories.isNotEmpty() -> body + "\n\n" + fromStories
            body.isNotEmpty() -> body
            else -> fromStories
        }
    }

    private fun normalizeAiFragmentStories(
        sorted: List<LifeFragment>,
        fromAi: List<FragmentAiStory>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val byId = fromAi.filter { it.text.isNotBlank() }.associateBy { it.fragmentId }.toMutableMap()
        val priorById = prior?.fragmentStories.orEmpty().associateBy { it.fragmentId }
        val priorSourceIds = prior?.sourceFragmentIds?.toSet().orEmpty()
        for (f in sorted) {
            val priorStory = priorById[f.id]?.text?.trim().orEmpty()
            // sourceFragmentIds 为空时（旧数据或未写入），仍可能有 fragmentStories；须保留，否则模型输出会盖掉时间线上的旧稿。
            val tiesToPriorSources = priorSourceIds.isEmpty() || f.id in priorSourceIds
            if (prior != null && priorStory.isNotEmpty() && tiesToPriorSources) {
                byId[f.id] = FragmentAiStory(f.id, priorStory)
                continue
            }
            if (byId[f.id]?.text.isNullOrBlank()) {
                val p = priorStory.takeIf { it.isNotEmpty() }
                val c = f.content.trim().takeIf { it.isNotEmpty() }
                val img = if (f.imageUris.isNotEmpty()) "${f.imageUris.size} 张图片记录" else ""
                val fb = p ?: c ?: img.takeIf { it.isNotEmpty() }
                if (fb != null) byId[f.id] = FragmentAiStory(f.id, fb)
            }
        }
        return sorted.map { fr ->
            byId[fr.id] ?: FragmentAiStory(fr.id, storyFallback(fr))
        }
    }

    private fun storyFallback(f: LifeFragment): String {
        val c = f.content.trim()
        if (c.isNotEmpty()) return c
        if (f.imageUris.isNotEmpty()) return "${f.imageUris.size} 张图片记录"
        return ""
    }

    private fun finalizeWithRuleGenerator(
        date: LocalDate,
        sorted: List<LifeFragment>,
        mergedImageUris: List<String>,
        pins: List<DiaryLocationPin>,
        priorSaved: DiaryEntry?
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted, priorSaved)
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = mergedImageUris,
            locationPins = pins
        )
    }

    /** 碎片图片在前，再把已保存手帐中的 URI 追加进来（去重）。 */
    private fun mergeDiaryImageUris(fragmentImages: List<String>, priorSaved: DiaryEntry?): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        fun addAll(uris: Iterable<String>) {
            for (u in uris) {
                val t = u.trim()
                if (t.isNotEmpty() && seen.add(t)) out.add(t)
            }
        }
        addAll(fragmentImages)
        addAll(priorSaved?.imageUris.orEmpty())
        return out
    }
}
