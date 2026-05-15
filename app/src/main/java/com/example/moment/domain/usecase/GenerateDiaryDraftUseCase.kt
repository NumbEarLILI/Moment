package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
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
        val imageUris = sorted.flatMap { it.imageUris }
        val pins = pinsFromFragments(sorted)

        if (sorted.isEmpty()) {
            val emptyDraft = diaryGenerator.generate(date, sorted, priorSaved)
            return emptyDraft.copy(imageUris = imageUris, locationPins = pins)
        }

        if (mode == DiaryGenerationMode.RULE_BASED_ONLY) {
            return finalizeWithRuleGenerator(date, sorted, imageUris, pins, priorSaved)
        }

        val prefs = userPreferencesAccessor.current()
        val config = prefs.toLlmConnectionConfig()
        if (config == null) {
            return finalizeWithRuleGenerator(date, sorted, imageUris, pins, priorSaved)
        }

        val aiDraft = aiDiaryDraftGenerator.generateDraft(date, sorted, config, priorSaved).getOrElse { throw it }
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
        pins: List<DiaryLocationPin>,
        priorSaved: DiaryEntry?
    ): DiaryDraft {
        val draft = diaryGenerator.generate(date, sorted, priorSaved)
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = imageUris,
            locationPins = pins
        )
    }
}
