package com.example.moment.domain.usecase

import com.example.moment.domain.diary.DiaryDraftComposer
import com.example.moment.domain.location.pinsFromFragments
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.repository.UserPreferencesRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GenerateDiaryDraftUseCase @Inject constructor(
    private val fragmentRepository: FragmentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val diaryDraftComposer: DiaryDraftComposer
) {
    suspend operator fun invoke(date: LocalDate): DiaryDraft {
        val fragments = fragmentRepository.getFragmentsForDate(date)
        val sorted = fragments.sortedBy { it.createdAt }
        val prefs = userPreferencesRepository.preferences.first()
        val draft = diaryDraftComposer.compose(date, sorted, prefs)
        val imageUris = sorted.flatMap { it.imageUris }
        val pins = pinsFromFragments(sorted)
        return draft.copy(
            sourceFragmentIds = sorted.map { it.id },
            imageUris = imageUris,
            locationPins = pins
        )
    }
}
