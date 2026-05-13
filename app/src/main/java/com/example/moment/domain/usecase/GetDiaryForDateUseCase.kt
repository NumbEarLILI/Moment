package com.example.moment.domain.usecase

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import java.time.LocalDate
import javax.inject.Inject

class GetDiaryForDateUseCase @Inject constructor(
    private val repository: DiaryRepository
) {
    suspend operator fun invoke(date: LocalDate): DiaryEntry? = repository.getDiaryForDate(date)
}
