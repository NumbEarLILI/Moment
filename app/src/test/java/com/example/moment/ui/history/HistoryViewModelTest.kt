package com.example.moment.ui.history

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveAllFragmentsUseCase
import com.example.moment.domain.usecase.ObserveDiaryEntriesUseCase
import com.example.moment.domain.usecase.ObserveFragmentsForDateUseCase
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun calendarDayClickWithSavedDiary_selectsDateAndKeepsHistoryInPlace() = runTest {
        val date = LocalDate.of(2026, 5, 20)
        val viewModel = historyViewModel(diaries = listOf(diary(date)))

        viewModel.onCalendarDayClick(date)
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.first { !it.isLoading }.selectedDate)
    }

    @Test
    fun calendarDayClickWithoutSavedDiary_selectsDateAndKeepsHistoryInPlace() = runTest {
        val date = LocalDate.of(2026, 5, 21)
        val viewModel = historyViewModel(diaries = emptyList())

        viewModel.onCalendarDayClick(date)
        advanceUntilIdle()

        assertEquals(date, viewModel.uiState.first { !it.isLoading }.selectedDate)
    }

    @Test
    fun selectedDayShowsOnlyThatDaysDiaries() = runTest {
        val selected = LocalDate.of(2026, 5, 20)
        val other = LocalDate.of(2026, 5, 21)
        val selectedDiary = diary(id = 7L, date = selected, title = "选中日手帐")
        val otherDiary = diary(id = 8L, date = other, title = "另一日手帐")
        val viewModel = historyViewModel(diaries = listOf(selectedDiary, otherDiary))

        viewModel.onCalendarDayClick(selected)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(setOf(selected, other), state.datesWithRecords)
        assertEquals(listOf(selectedDiary), state.diaryEntries)
    }

    @Test
    fun calendarMarksFragmentDaysEvenWithoutDiary() = runTest {
        val fragmentDay = LocalDate.of(2026, 5, 18)
        val diaryDay = LocalDate.of(2026, 5, 20)
        val viewModel = historyViewModel(
            fragments = listOf(fragment(id = 1L, date = fragmentDay)),
            diaries = listOf(diary(date = diaryDay))
        )

        viewModel.onCalendarDayClick(diaryDay)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(setOf(fragmentDay, diaryDay), state.datesWithRecords)
    }

    @Test
    fun calendarUsesLocalDateWhenMarkingFragmentDays() = runTest {
        val zoneId = java.time.ZoneId.of("Asia/Shanghai")
        val fragment = fragment(
            id = 1L,
            date = LocalDate.of(2026, 5, 12)
        ).copy(createdAt = Instant.parse("2026-05-12T16:30:00Z"))
        val viewModel = historyViewModel(
            fragments = listOf(fragment),
            zoneId = zoneId
        )

        viewModel.onCalendarDayClick(LocalDate.of(2026, 5, 13))
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(setOf(LocalDate.of(2026, 5, 13)), state.datesWithRecords)
    }

    @Test
    fun canGenerateDiaryWhenSelectedDayHasFragments() = runTest {
        val date = LocalDate.of(2026, 5, 20)
        val viewModel = historyViewModel(
            fragments = listOf(fragment(id = 1L, date = date)),
            diaries = emptyList()
        )

        viewModel.onCalendarDayClick(date)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(true, state.canGenerateDiary)
    }

    @Test
    fun cannotGenerateDiaryWhenSelectedDayHasNoFragments() = runTest {
        val date = LocalDate.of(2026, 5, 20)
        val viewModel = historyViewModel(
            fragments = listOf(fragment(id = 1L, date = date.minusDays(1))),
            diaries = listOf(diary(date = date))
        )

        viewModel.onCalendarDayClick(date)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(false, state.canGenerateDiary)
        assertEquals(listOf(diary(date = date)), state.diaryEntries)
    }

    private fun historyViewModel(
        fragments: List<LifeFragment> = emptyList(),
        diaries: List<DiaryEntry> = emptyList(),
        zoneId: java.time.ZoneId = java.time.ZoneOffset.UTC
    ): HistoryViewModel {
        val fragmentRepository = FakeFragmentRepository(fragments)
        val diaryRepository = FakeDiaryRepository(diaries)
        return HistoryViewModel(
            observeFragmentsForDate = ObserveFragmentsForDateUseCase(fragmentRepository),
            observeAllFragments = ObserveAllFragmentsUseCase(fragmentRepository),
            deleteFragment = DeleteFragmentUseCase(fragmentRepository),
            observeDiaryEntries = ObserveDiaryEntriesUseCase(diaryRepository),
            zoneId = zoneId
        )
    }

    private fun diary(
        date: LocalDate,
        id: Long = 7L,
        title: String = "已保存手帐"
    ) = DiaryEntry(
        id = id,
        date = date,
        title = title,
        body = "正文",
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = emptyList(),
        createdAt = Instant.parse("2026-05-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-05-20T09:00:00Z")
    )

    private fun fragment(id: Long, date: LocalDate) = LifeFragment(
        id = id,
        stableId = "fragment-$id",
        content = "碎片 $id",
        imageUris = emptyList(),
        mood = null,
        tags = emptyList(),
        createdAt = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(id * 60),
        updatedAt = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
    )

    private class FakeFragmentRepository(
        private val fragments: List<LifeFragment>
    ) : FragmentRepository {
        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> =
            MutableStateFlow(
                fragments.filter {
                    java.time.LocalDate.ofInstant(it.createdAt, java.time.ZoneOffset.UTC) == date
                }
            )

        override fun observeAllFragments(): Flow<List<LifeFragment>> =
            MutableStateFlow(fragments)

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> = fragments
        override suspend fun getFragmentsForStableIds(stableIds: List<String>): List<LifeFragment> =
            fragments.filter { it.stableId in stableIds }

        override suspend fun getFragmentById(id: Long): LifeFragment? =
            fragments.find { it.id == id }

        override suspend fun getFragmentByStableId(stableId: String): LifeFragment? =
            fragments.find { it.stableId == stableId }

        override suspend fun addFragment(fragment: LifeFragment): Long = fragment.id
        override suspend fun updateFragment(fragment: LifeFragment) = Unit
        override suspend fun deleteFragment(id: Long) = Unit
        override suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry) = Unit
    }

    private class FakeDiaryRepository(
        diaries: List<DiaryEntry>
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
