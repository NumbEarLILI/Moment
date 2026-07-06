package com.example.moment.ui.history

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DeleteFragmentUseCase
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

    private fun historyViewModel(
        fragments: List<LifeFragment> = emptyList(),
        diaries: List<DiaryEntry> = emptyList()
    ): HistoryViewModel {
        val fragmentRepository = FakeFragmentRepository(fragments)
        val diaryRepository = FakeDiaryRepository(diaries)
        return HistoryViewModel(
            observeFragmentsForDate = ObserveFragmentsForDateUseCase(fragmentRepository),
            deleteFragment = DeleteFragmentUseCase(fragmentRepository),
            observeDiaryEntries = ObserveDiaryEntriesUseCase(diaryRepository)
        )
    }

    private fun diary(date: LocalDate) = DiaryEntry(
        id = 7L,
        date = date,
        title = "已保存手帐",
        body = "正文",
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = emptyList(),
        createdAt = Instant.parse("2026-05-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-05-20T09:00:00Z")
    )

    private class FakeFragmentRepository(
        private val fragments: List<LifeFragment>
    ) : FragmentRepository {
        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> =
            MutableStateFlow(fragments)

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
