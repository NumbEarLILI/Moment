package com.example.moment.domain.usecase

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.FragmentAiStory
import com.example.moment.domain.model.isNasGhostPlaceholder
import com.example.moment.domain.nas.NasArchiveSyncCoordinator
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveDiaryUseCase @Inject constructor(
    private val repository: DiaryRepository,
    private val fragmentRepository: FragmentRepository,
    private val nasArchiveSync: NasArchiveSyncCoordinator,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        date: LocalDate,
        title: String,
        body: String,
        highlights: List<String>,
        moodSummary: String?,
        sourceFragmentStableIds: List<String>,
        imageUris: List<String>,
        locationPins: List<DiaryLocationPin>,
        fragmentStories: List<FragmentAiStory>,
        fragmentImageUris: Map<String, List<String>>,
        fragmentCreatedAtEpochMillis: Map<String, Long> = emptyMap()
    ): Long {
        val now = clock.instant()
        val existing = repository.getDiaryForDate(date)
        val savedFragmentCreatedAtEpochMillis = fragmentCreatedAtEpochMillisForSave(
            sourceFragmentStableIds,
            existing,
            fragmentCreatedAtEpochMillis
        )
        val entry = DiaryEntry(
            id = existing?.id ?: 0,
            date = date,
            title = title.trim().ifEmpty { "未命名的一天" },
            body = body.trim(),
            highlights = highlights,
            moodSummary = moodSummary,
            sourceFragmentStableIds = sourceFragmentStableIds,
            imageUris = imageUris,
            locationPins = locationPins,
            fragmentStories = fragmentStories,
            fragmentImageUris = fragmentImageUris,
            fragmentCreatedAtEpochMillis = savedFragmentCreatedAtEpochMillis,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val savedId = repository.saveDiary(entry)
        val persisted = repository.getDiaryById(savedId)
            ?: repository.getDiaryForDate(date)
        if (persisted != null) {
            nasArchiveSync.onDiarySaved(persisted).getOrThrow()
        }
        return savedId
    }

    private suspend fun fragmentCreatedAtEpochMillisForSave(
        sourceFragmentStableIds: List<String>,
        existing: DiaryEntry?,
        editedTimes: Map<String, Long>
    ): Map<String, Long> {
        val orderedStableIds = sourceFragmentStableIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (orderedStableIds.isEmpty()) return emptyMap()
        val existingTimes = existing?.fragmentCreatedAtEpochMillis.orEmpty()
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotEmpty() }
        val editedClean = editedTimes
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotEmpty() }
        val liveTimes = fragmentRepository.getFragmentsForStableIds(orderedStableIds)
            .filterNot { it.isNasGhostPlaceholder() }
            .associate { it.stableId.trim() to it.createdAt.toEpochMilli() }
        return orderedStableIds.mapNotNull { sid ->
            val epochMillis = editedClean[sid] ?: liveTimes[sid] ?: existingTimes[sid] ?: return@mapNotNull null
            sid to epochMillis
        }.toMap()
    }
}
