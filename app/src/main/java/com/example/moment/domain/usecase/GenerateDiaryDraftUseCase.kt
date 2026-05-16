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
        return aiDraft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = mergedImageUris,
            locationPins = pins,
            fragmentStories = filledStories
        )
    }

    private fun normalizeAiFragmentStories(
        sorted: List<LifeFragment>,
        fromAi: List<FragmentAiStory>,
        prior: DiaryEntry?
    ): List<FragmentAiStory> {
        val byId = fromAi.filter { it.text.isNotBlank() }.associateBy { it.fragmentId }.toMutableMap()
        val priorById = prior?.fragmentStories.orEmpty().associateBy { it.fragmentId }
        for (f in sorted) {
            if (byId[f.id]?.text.isNullOrBlank()) {
                val p = priorById[f.id]?.text?.trim().orEmpty().takeIf { it.isNotEmpty() }
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
