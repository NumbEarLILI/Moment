package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.toLlmConnectionConfig
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import javax.inject.Inject

class GenerateDiaryDraftUseCase @Inject constructor(
    private val fragmentRepository: FragmentRepository,
    private val diaryGenerator: DiaryGenerator,
    private val userPreferencesAccessor: UserPreferencesAccessor,
    private val aiDiaryDraftGenerator: AiDiaryDraftGenerator
) {
    suspend operator fun invoke(
        date: LocalDate,
        mode: DiaryGenerationMode = DiaryGenerationMode.AUTO
    ): DiaryDraft {
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val imageUris = sorted.flatMap { it.imageUris }
        val pins = pinsFromFragments(sorted)

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted)
            return emptyDraft.copy(imageUris = imageUris, locationPins = pins)
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(date, sorted, imageUris, pins)
        }

        val prefs = userPreferencesAccessor.current()
        val config = prefs.toLlmConnectionConfig()
        if (config == null) {
            return finalizeWithRuleGenerator(date, sorted, imageUris, pins)
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config).getOrElse { throw it }
        return aiDraft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = imageUris,
            locationPins = pins
        )
    }

    private fun finalizeWithRuleGenerator(
        date: LocalDate,
        sorted: List<LifeFragment>,
        imageUris: List<String>,
        pins: List<DiaryLocationPin>
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted)
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = imageUris,
            locationPins = pins
        )
    }
}
