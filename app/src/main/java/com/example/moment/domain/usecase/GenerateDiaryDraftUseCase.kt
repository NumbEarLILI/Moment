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
        val draft = diaryGenerator.generate(date, fragments)
        return draft.copy(sourceFragmentIds = fragments.map { it.id })
    }
}
