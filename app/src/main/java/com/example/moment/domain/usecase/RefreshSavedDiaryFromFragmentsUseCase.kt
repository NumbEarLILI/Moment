package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import javax.inject.Inject

class RefreshSavedDiaryFromFragmentsUseCase @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val fragmentRepository: FragmentRepository,
    private val diaryGenerator: DiaryGenerator,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(diaryId: Long) {
        val entry = diaryRepository.getDiaryById(diaryId) ?: return
        val fragments = fragmentRepository.getFragmentsForDate(entry.date).sortedBy { it.createdAt }
        val draft = diaryGenerator.generate(entry.date, fragments, null)
        val imageUris = fragments.flatMap { it.imageUris }
        val pins = pinsFromFragments(fragments)
        diaryRepository.saveDiary(
            entry.copy(
                title = draft.title,
                body = draft.body,
                highlights = draft.highlights,
                moodSummary = draft.moodSummary,
                sourceFragmentIds = draft.sourceFragmentIds,
                imageUris = imageUris,
                locationPins = pins,
                updatedAt = clock.instant()
            )
        )
    }
}
