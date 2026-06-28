package com.example.moment.ui.timeline

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveAllFragmentsUseCase
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FragmentTimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateShowsAllRealFragmentsInRepositoryOrder() = runTest {
        val newest = fragment(id = 3L, stableId = "newest", content = "最新碎片")
        val ghost = fragment(id = 2L, stableId = "ghost", content = "")
        val older = fragment(id = 1L, stableId = "older", content = "较早碎片")
        val repo = FakeFragmentRepository(listOf(newest, ghost, older))
        val viewModel = timelineViewModel(fragmentRepository = repo)

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(newest, older), state.fragments)
        assertEquals(listOf(FragmentTimelineItem.Fragment(newest), FragmentTimelineItem.Fragment(older)), state.items)
    }

    @Test
    fun deleteRemovesFragmentFromTimeline() = runTest {
        val first = fragment(id = 1L, stableId = "first", content = "第一条")
        val second = fragment(id = 2L, stableId = "second", content = "第二条")
        val repo = FakeFragmentRepository(listOf(second, first))
        val viewModel = timelineViewModel(fragmentRepository = repo)

        viewModel.delete(first.id)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(second), state.fragments)
    }

    @Test
    fun deleteFailureExposesMessageAndClearsDeletingState() = runTest {
        val first = fragment(id = 1L, stableId = "first", content = "第一条")
        val repo = FakeFragmentRepository(listOf(first), failDeletes = true)
        val viewModel = timelineViewModel(fragmentRepository = repo)

        viewModel.delete(first.id)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(first), state.fragments)
        assertEquals("删除碎片失败", state.deleteErrorMessage)
        assertNull(state.deletingFragmentId)
    }

    @Test
    fun uiStateShowsDiaryWhenThatDayHasNoFragments() = runTest {
        val fragment = fragment(
            id = 1L,
            stableId = "fragment",
            content = "5月13日碎片",
            createdAt = Instant.parse("2026-05-13T10:00:00Z")
        )
        val diary = diary(
            id = 7L,
            date = LocalDate.of(2026, 5, 12),
            title = "5月12日手帐",
            body = "这一天只有手帐内容"
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(fragment)),
            diaryRepository = FakeDiaryRepository(listOf(diary))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(
            listOf(
                FragmentTimelineItem.Fragment(fragment),
                FragmentTimelineItem.DiaryFallback(diary)
            ),
            state.items
        )
    }

    @Test
    fun uiStateDoesNotShowDiaryFallbackWhenThatDayHasFragments() = runTest {
        val fragment = fragment(
            id = 1L,
            stableId = "fragment",
            content = "当天碎片",
            createdAt = Instant.parse("2026-05-12T10:00:00Z")
        )
        val diary = diary(
            id = 7L,
            date = LocalDate.of(2026, 5, 12),
            title = "当天手帐",
            body = "当天已有碎片时不重复显示"
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(fragment)),
            diaryRepository = FakeDiaryRepository(listOf(diary))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(FragmentTimelineItem.Fragment(fragment)), state.items)
    }

    @Test
    fun uiStateUsesLocalDateWhenSuppressingDiaryFallback() = runTest {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val fragment = fragment(
            id = 1L,
            stableId = "fragment",
            content = "本地 5月13日碎片",
            createdAt = Instant.parse("2026-05-12T16:30:00Z")
        )
        val diary = diary(
            id = 7L,
            date = LocalDate.of(2026, 5, 13),
            title = "5月13日手帐",
            body = "UTC 仍是前一天，但本地日期已有碎片"
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(fragment)),
            diaryRepository = FakeDiaryRepository(listOf(diary)),
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(FragmentTimelineItem.Fragment(fragment)), state.items)
    }

    @Test
    fun uiStateSortsFragmentsAndDiaryFallbacksNewestFirst() = runTest {
        val fragment = fragment(
            id = 1L,
            stableId = "fragment",
            content = "5月13日碎片",
            createdAt = Instant.parse("2026-05-13T10:00:00Z")
        )
        val newerDiary = diary(
            id = 8L,
            date = LocalDate.of(2026, 5, 14),
            title = "5月14日手帐",
            body = "较新的无碎片日期"
        )
        val olderDiary = diary(
            id = 7L,
            date = LocalDate.of(2026, 5, 12),
            title = "5月12日手帐",
            body = "较早的无碎片日期"
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(fragment)),
            diaryRepository = FakeDiaryRepository(listOf(olderDiary, newerDiary))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(
            listOf(
                FragmentTimelineItem.DiaryFallback(newerDiary),
                FragmentTimelineItem.Fragment(fragment),
                FragmentTimelineItem.DiaryFallback(olderDiary)
            ),
            state.items
        )
    }

    private fun timelineViewModel(
        fragmentRepository: FakeFragmentRepository,
        diaryRepository: FakeDiaryRepository = FakeDiaryRepository(),
        zoneId: ZoneId = ZoneOffset.UTC
    ): FragmentTimelineViewModel =
        FragmentTimelineViewModel(
            observeAllFragments = ObserveAllFragmentsUseCase(fragmentRepository),
            observeDiaryEntries = ObserveDiaryEntriesUseCase(diaryRepository),
            deleteFragment = DeleteFragmentUseCase(fragmentRepository),
            zoneId = zoneId
        )

    private fun fragment(
        id: Long,
        stableId: String,
        content: String,
        createdAt: Instant = Instant.parse("2026-05-13T10:00:00Z").plusSeconds(id)
    ) = LifeFragment(
        id = id,
        stableId = stableId,
        content = content,
        imageUris = emptyList(),
        mood = null,
        tags = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun diary(
        id: Long,
        date: LocalDate,
        title: String,
        body: String
    ) = DiaryEntry(
        id = id,
        date = date,
        title = title,
        body = body,
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = emptyList(),
        createdAt = date.atStartOfDay().toInstant(ZoneOffset.UTC),
        updatedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC)
    )

    private class FakeFragmentRepository(
        initial: List<LifeFragment>,
        private val failDeletes: Boolean = false
    ) : FragmentRepository {
        private val fragments = MutableStateFlow(initial)

        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> = fragments

        override fun observeAllFragments(): Flow<List<LifeFragment>> = fragments

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> = fragments.value

        override suspend fun getFragmentsForStableIds(stableIds: List<String>): List<LifeFragment> =
            fragments.value.filter { it.stableId in stableIds }

        override suspend fun getFragmentById(id: Long): LifeFragment? =
            fragments.value.find { it.id == id }

        override suspend fun getFragmentByStableId(stableId: String): LifeFragment? =
            fragments.value.find { it.stableId == stableId }

        override suspend fun addFragment(fragment: LifeFragment): Long = fragment.id

        override suspend fun updateFragment(fragment: LifeFragment) = Unit

        override suspend fun deleteFragment(id: Long) {
            if (failDeletes) error("delete failed")
            fragments.value = fragments.value.filterNot { it.id == id }
        }

        override suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry) = Unit
    }

    private class FakeDiaryRepository(
        diaries: List<DiaryEntry> = emptyList()
    ) : DiaryRepository {
        private val state = MutableStateFlow(diaries)

        override fun observeDiaries(): Flow<List<DiaryEntry>> = state

        override fun observeDiary(id: Long): Flow<DiaryEntry?> =
            MutableStateFlow(state.value.find { it.id == id })

        override suspend fun getDiaryForDate(date: LocalDate): DiaryEntry? =
            state.value.find { it.date == date }

        override suspend fun getDiaryById(id: Long): DiaryEntry? =
            state.value.find { it.id == id }

        override suspend fun getAllDiaries(): List<DiaryEntry> = state.value

        override suspend fun saveDiary(entry: DiaryEntry): Long = entry.id

        override suspend fun deleteDiaryById(id: Long) = Unit
    }
}
