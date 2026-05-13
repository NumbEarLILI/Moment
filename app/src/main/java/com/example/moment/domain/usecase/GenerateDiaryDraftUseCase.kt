package com.example.moment.domain.usecase

import com.example.moment.domain.generator.DiaryGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import javax.inject.Inject

class GenerateDiaryDraftUseCase @Inject constructor(
    private val fragmentRepository: FragmentRepository,
    private val diaryGenerator: DiaryGenerator
) {
    suspend operator fun invoke(date: LocalDate): DiaryDraft {
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val draft = diaryGenerator.generate(date, sorted)
        val imageUris = sorted.flatMap { it.imageUris }
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = imageUris
        )
    }
}
