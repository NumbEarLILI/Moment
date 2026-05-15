package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.toLlmConnectionConfig
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import java.util.LinkedHashSet
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
        val prior = diaryRepository.getDiaryForDate(date)
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val fragmentImageUris = sorted.flatMap { it.imageUris }
        val fragmentPins = pinsFromFragments(sorted)
        val mergedImageUris = mergeImageUrisWithPrior(prior?.imageUris, fragmentImageUris)
        val mergedPins = mergePinsWithPrior(prior, sorted, fragmentPins)

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted)
            return emptyDraft.copy(
                imageUris = mergedImageUris,
                locationPins = mergedPins
            )
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(date, sorted, fragmentImageUris, fragmentPins, prior)
        }

        val prefs = userPreferencesAccessor.current()
        val config = prefs.toLlmConnectionConfig()
        if (config == null) {
            return finalizeWithRuleGenerator(date, sorted, fragmentImageUris, fragmentPins, prior)
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config).getOrElse { throw it }
        return aiDraft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = mergedImageUris,
            locationPins = mergedPins
        )
    }

    private fun finalizeWithRuleGenerator(
        date: LocalDate,
        sorted: List<LifeFragment>,
        fragmentImageUris: List<String>,
        fragmentPins: List<DiaryLocationPin>,
        prior: DiaryEntry?
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted)
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = mergeImageUrisWithPrior(prior?.imageUris, fragmentImageUris),
            locationPins = mergePinsWithPrior(prior, sorted, fragmentPins)
        )
    }

    /** 先保留已保存手帐中的图片顺序，再追加当前碎片中的图片（去重）。 */
    private fun mergeImageUrisWithPrior(priorUris: List<String>?, fragmentUris: List<String>): List<String> {
        val order = LinkedHashSet<String>()
        priorUris.orEmpty().forEach { u -> if (u.isNotBlank()) order.add(u) }
        fragmentUris.forEach { u -> if (u.isNotBlank()) order.add(u) }
        return order.toList()
    }

    /** 当前碎片上的地点优先；已保存手帐里「已删碎片」上的地点仍保留。 */
    private fun mergePinsWithPrior(
        prior: DiaryEntry?,
        sorted: List<LifeFragment>,
        fragmentPins: List<DiaryLocationPin>
    ): List<DiaryLocationPin> {
        if (prior == null) return fragmentPins
        val fragIds = sorted.map { it.id }.toSet()
        val orphanPriorPins = prior.locationPins.filter { it.fragmentId !in fragIds }
        return fragmentPins + orphanPriorPins
    }
}
