package com.example.moment.domain.usecase

import com.example.moment.domain.repository.DiaryRepository
import javax.inject.Inject

class DeleteDiaryUseCase @Inject constructor(
    private val repository: DiaryRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteDiaryById(id)
    }
}
