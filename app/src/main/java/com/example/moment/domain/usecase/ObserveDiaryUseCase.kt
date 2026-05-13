package com.example.moment.domain.usecase

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDiaryUseCase @Inject constructor(
    private val repository: DiaryRepository
) {
    operator fun invoke(id: Long): Flow<DiaryEntry?> = repository.observeDiary(id)
}
