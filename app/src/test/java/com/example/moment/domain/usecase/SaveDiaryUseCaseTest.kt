package com.example.moment.domain.usecase

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.nas.NasArchiveSyncCoordinator
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveDiaryUseCaseTest {

    @Test
    fun invokeRefreshesFragmentCreatedAtFromLiveFragmentWhenAvailable() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val oldTime = Instant.parse("2026-05-13T08:00:00Z")
        val customTime = Instant.parse("2026-05-13T14:30:00Z")
        val existingDiary = diary(
            date = date,
            fragmentCreatedAtEpochMillis = mapOf("s1" to oldTime.toEpochMilli())
        )
        val diaryRepository = FakeDiaryRepository(existingDiary)
        val fragmentRepository = FakeFragmentRepository(
            listOf(
                LifeFragment(
                    id = 1,
                    stableId = "s1",
                    content = "今天的 plog",
                    imageUris = emptyList(),
                    mood = Mood.CALM,
                    tags = emptyList(),
                    createdAt = customTime,
                    updatedAt = customTime
                )
            )
        )
        val useCase = SaveDiaryUseCase(
            repository = diaryRepository,
            fragmentRepository = fragmentRepository,
            nasArchiveSync = NoopNasArchiveSync,
            clock = Clock.fixed(Instant.parse("2026-05-14T00:00:00Z"), ZoneOffset.UTC)
        )

        useCase(
            date = date,
            title = "标题",
            body = "正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentStableIds = listOf("s1"),
            imageUris = emptyList(),
            locationPins = emptyList(),
            fragmentStories = emptyList(),
            fragmentImageUris = emptyMap()
        )

        assertEquals(
            mapOf("s1" to customTime.toEpochMilli()),
            diaryRepository.saved.value.single().fragmentCreatedAtEpochMillis
        )
    }

    private fun diary(
        date: LocalDate,
        fragmentCreatedAtEpochMillis: Map<String, Long>
    ) = DiaryEntry(
        id = 7,
        date = date,
        title = "旧标题",
        body = "旧正文",
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = listOf("s1"),
        imageUris = emptyList(),
        locationPins = emptyList(),
        fragmentStories = emptyList(),
        fragmentImageUris = emptyMap(),
        fragmentCreatedAtEpochMillis = fragmentCreatedAtEpochMillis,
        createdAt = Instant.parse("2026-05-13T10:00:00Z"),
        updatedAt = Instant.parse("2026-05-13T10:00:00Z")
    )

    private class FakeDiaryRepository(initial: DiaryEntry) : DiaryRepository {
        val saved = MutableStateFlow(listOf(initial))

        override fun observeDiaries(): Flow<List<DiaryEntry>> = saved
        override fun observeDiary(id: Long): Flow<DiaryEntry?> = MutableStateFlow(saved.value.find { it.id == id })
        override suspend fun getDiaryForDate(date: LocalDate): DiaryEntry? =
            saved.value.find { it.date == date }

        override suspend fun getDiaryById(id: Long): DiaryEntry? =
            saved.value.find { it.id == id }

        override suspend fun getAllDiaries(): List<DiaryEntry> = saved.value

        override suspend fun saveDiary(entry: DiaryEntry): Long {
            val savedEntry = entry.copy(id = entry.id.takeIf { it > 0 } ?: 1)
            saved.value = listOf(savedEntry)
            return savedEntry.id
        }

        override suspend fun deleteDiaryById(id: Long) = Unit
    }

    private class FakeFragmentRepository(
        private val fragments: List<LifeFragment>
    ) : FragmentRepository {
        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> =
            MutableStateFlow(fragments)

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> = fragments

        override suspend fun getFragmentsForStableIds(stableIds: List<String>): List<LifeFragment> {
            val byStableId = fragments.associateBy { it.stableId }
            return stableIds.mapNotNull { byStableId[it] }
        }

        override suspend fun getFragmentByStableId(stableId: String): LifeFragment? =
            fragments.find { it.stableId == stableId }

        override suspend fun getFragmentById(id: Long): LifeFragment? =
            fragments.find { it.id == id }

        override suspend fun addFragment(fragment: LifeFragment): Long = 0L
        override suspend fun updateFragment(fragment: LifeFragment) = Unit
        override suspend fun deleteFragment(id: Long) = Unit
        override suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry) = Unit
    }

    private object NoopNasArchiveSync : NasArchiveSyncCoordinator {
        override suspend fun onDiarySaved(entry: DiaryEntry) = Unit
        override suspend fun onDiaryDeleted(dateEpochDay: Long) = Unit
    }
}
