package com.example.moment.domain.usecase

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.repository.FragmentRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateFragmentUseCaseTest {
    @Test
    fun invokeUpdatesContentAndPreservesCreatedAt() = runTest {
        val created = Instant.parse("2026-05-13T10:00:00Z")
        val existing = LifeFragment(
            id = 1,
            content = "旧内容",
            imageUris = emptyList(),
            mood = Mood.CALM,
            tags = listOf("a"),
            createdAt = created,
            updatedAt = created,
            location = FragmentLocation(39.9042, 116.4074, "北京市")
        )
        val repo = FakeFragmentRepository(listOf(existing))
        val useCase = UpdateFragmentUseCase(
            repository = repo,
            clock = java.time.Clock.fixed(
                Instant.parse("2026-05-13T18:00:00Z"),
                java.time.ZoneOffset.UTC
            )
        )

        val result = useCase(
            id = 1,
            content = "新内容",
            imageUris = emptyList(),
            mood = Mood.FOCUSED,
            tags = listOf("b", "c")
        )

        assertEquals(UpdateFragmentResult.Saved, result)
        val updated = repo.fragments.value.single()
        assertEquals("新内容", updated.content)
        assertEquals(created, updated.createdAt)
        assertEquals(Mood.FOCUSED, updated.mood)
        assertEquals(listOf("b", "c"), updated.tags)
        assertEquals(Instant.parse("2026-05-13T18:00:00Z"), updated.updatedAt)
        assertEquals(FragmentLocation(39.9042, 116.4074, "北京市"), updated.location)
    }

    @Test
    fun invokeReturnsNotFoundWhenMissing() = runTest {
        val repo = FakeFragmentRepository(emptyList())
        val useCase = UpdateFragmentUseCase(repository = repo)

        val result = useCase(
            id = 99,
            content = "x",
            imageUris = listOf("uri"),
            mood = null,
            tags = emptyList()
        )

        assertEquals(UpdateFragmentResult.NotFound, result)
    }

    @Test
    fun invokeReturnsEmptyWhenClearedToBlank() = runTest {
        val existing = LifeFragment(
            id = 1,
            content = "有字",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = Instant.parse("2026-05-13T10:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T10:00:00Z")
        )
        val repo = FakeFragmentRepository(listOf(existing))
        val useCase = UpdateFragmentUseCase(repository = repo)

        val result = useCase(
            id = 1,
            content = "   ",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList()
        )

        assertEquals(UpdateFragmentResult.Empty, result)
        assertEquals("有字", repo.fragments.value.single().content)
    }

    private class FakeFragmentRepository(
        initial: List<LifeFragment>
    ) : FragmentRepository {
        val fragments = MutableStateFlow(initial)

        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> = fragments

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> = fragments.value

        override suspend fun getFragmentById(id: Long): LifeFragment? =
            fragments.value.find { it.id == id }

        override suspend fun addFragment(fragment: LifeFragment): Long = 0L

        override suspend fun updateFragment(fragment: LifeFragment) {
            fragments.value = fragments.value.map { if (it.id == fragment.id) fragment else it }
        }

        override suspend fun deleteFragment(id: Long) = Unit
    }
}
