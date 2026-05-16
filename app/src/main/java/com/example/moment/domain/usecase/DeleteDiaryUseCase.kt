package com.example.moment.domain.usecase

import com.example.moment.domain.nas.NasArchiveSyncCoordinator
import com.example.moment.domain.repository.DiaryRepository
import javax.inject.Inject

class DeleteDiaryUseCase @Inject constructor(
    private val repository: DiaryRepository,
    private val nasArchiveSync: NasArchiveSyncCoordinator
) {
    suspend operator fun invoke(id: Long) {
        val entry = repository.getDiaryById(id)
        val epoch = entry?.date?.toEpochDay()
        repository.deleteDiaryById(id)
        if (epoch != null) {
            nasArchiveSync.onDiaryDeleted(epoch)
        }
    }
}
