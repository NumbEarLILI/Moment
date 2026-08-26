package com.example.moment.ui.timeline

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.usecase.DeleteFragmentUseCase
import com.example.moment.domain.usecase.ObserveAllFragmentsUseCase
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
    fun uiStateShowsOnlyFragments() = runTest {
        val fragment = fragment(
            id = 1L,
            stableId = "fragment",
            content = "5月13日碎片",
            createdAt = Instant.parse("2026-05-13T10:00:00Z")
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(fragment))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf(FragmentTimelineItem.Fragment(fragment)), state.items)
    }

    @Test
    fun uiStateSortsFragmentsNewestFirst() = runTest {
        val older = fragment(
            id = 1L,
            stableId = "older",
            content = "较早碎片",
            createdAt = Instant.parse("2026-05-12T10:00:00Z")
        )
        val newer = fragment(
            id = 2L,
            stableId = "newer",
            content = "较新碎片",
            createdAt = Instant.parse("2026-05-13T10:00:00Z")
        )
        val viewModel = timelineViewModel(
            fragmentRepository = FakeFragmentRepository(listOf(older, newer))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(
            listOf(
                FragmentTimelineItem.Fragment(newer),
                FragmentTimelineItem.Fragment(older)
            ),
            state.items
        )
    }

    private fun timelineViewModel(
        fragmentRepository: FakeFragmentRepository
    ): FragmentTimelineViewModel =
        FragmentTimelineViewModel(
            observeAllFragments = ObserveAllFragmentsUseCase(fragmentRepository),
            deleteFragment = DeleteFragmentUseCase(fragmentRepository)
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
}
