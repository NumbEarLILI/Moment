package com.example.moment.domain.usecase

import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.repository.FragmentRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDiaryDraftUseCaseTest {
    @Test
    fun invokeGeneratesDraftFromFragmentsOnRequestedDate() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "今天午后完成了拖延很久的小任务。", Mood.FOCUSED, "2026-05-13T14:00:00Z"),
                fragment(2, "前一天的记录不应该进入今天的日记。", Mood.CALM, "2026-05-12T14:00:00Z")
            )
        )
        val useCase = GenerateDiaryDraftUseCase(repository, RuleBasedDiaryGenerator())

        val result = useCase(date)

        assertTrue(result.body.contains("今天午后完成了拖延很久的小任务"))
        assertTrue(result.sourceFragmentIds.contains(1))
        assertTrue(!result.sourceFragmentIds.contains(2))
    }

    @Test
    fun addFragmentRejectsCompletelyEmptyInput() = runTest {
        val repository = FakeFragmentRepository(emptyList())
        val useCase = AddFragmentUseCase(repository)

        val result = useCase(content = "   ", imageUris = emptyList(), mood = null, tags = emptyList())

        assertEquals(AddFragmentResult.Empty, result)
        assertTrue(repository.savedFragments.isEmpty())
    }

    private class FakeFragmentRepository(
        initialFragments: List<LifeFragment>
    ) : FragmentRepository {
        val savedFragments = mutableListOf<LifeFragment>()
        private val fragments = MutableStateFlow(initialFragments)

        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> = fragments

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> =
            fragments.value.filter { LocalDate.ofInstant(it.createdAt, java.time.ZoneId.systemDefault()) == date }

        override suspend fun addFragment(fragment: LifeFragment): Long {
            savedFragments += fragment
            return savedFragments.size.toLong()
        }

        override suspend fun deleteFragment(id: Long) = Unit
    }

    private fun fragment(
        id: Long,
        content: String,
        mood: Mood,
        createdAt: String
    ): LifeFragment = LifeFragment(
        id = id,
        content = content,
        imageUris = emptyList(),
        mood = mood,
        tags = emptyList(),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(createdAt)
    )
}
