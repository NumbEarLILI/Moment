package com.example.moment.domain.usecase

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveDiaryUseCase @Inject constructor(
    private val repository: DiaryRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        date: LocalDate,
        title: String,
        body: String,
        highlights: List<String>,
        moodSummary: String?,
        sourceFragmentIds: List<Long>,
        imageUris: List<String>
    ): Long {
        val now = clock.instant()
        val existing = repository.getDiaryForDate(date)
        val entry = DiaryEntry(
            id = existing?.id ?: 0,
            date = date,
            title = title.trim().ifEmpty { "未命名的一天" },
            body = body.trim(),
            highlights = highlights,
            moodSummary = moodSummary,
            sourceFragmentIds = sourceFragmentIds,
            imageUris = imageUris,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return repository.saveDiary(entry)
    }
}
