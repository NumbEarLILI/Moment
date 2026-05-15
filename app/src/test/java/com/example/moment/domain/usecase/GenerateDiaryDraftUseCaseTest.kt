package com.example.moment.domain.usecase

import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.LlmConnectionConfig
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.DiaryRepository
import com.example.moment.domain.repository.FragmentRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(null),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(UserAppPreferences()),
            NeverCalledAi
        )

        val result = useCase(date, DiaryGenerationMode.RULE_BASED_ONLY)

        assertTrue(result.body.contains("今天午后完成了拖延很久的小任务"))
        assertTrue(result.sourceFragmentIds.contains(1))
        assertTrue(!result.sourceFragmentIds.contains(2))
    }

    @Test
    fun invokeUsesAiWhenConfiguredAndModeAuto() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "只言片语。", Mood.HAPPY, "2026-05-13T10:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val aiDraft = DiaryDraft(
            title = "AI 标题",
            body = "AI 正文",
            highlights = listOf("AI 亮点"),
            moodSummary = "开心",
            sourceFragmentIds = listOf(99L)
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig
            ): Result<DiaryDraft> {
                assertEquals("https://example.com/v1", config.baseUrl)
                assertEquals("k", config.apiKey)
                assertEquals("m", config.model)
                assertEquals(1, fragments.size)
                return Result.success(aiDraft)
            }
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(null),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertEquals("AI 标题", result.title)
        assertEquals("AI 正文", result.body)
        assertEquals(listOf(1L), result.sourceFragmentIds)
    }

    @Test
    fun invokeIncludesLocationPinsForFragmentsWithLocation() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "有地点的记录。", Mood.CALM, "2026-05-13T12:00:00Z").copy(
                    location = FragmentLocation(31.23, 121.47, label = "上海某地")
                )
            )
        )
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(null),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(UserAppPreferences()),
            NeverCalledAi
        )

        val result = useCase(date, DiaryGenerationMode.RULE_BASED_ONLY)

        assertEquals(1, result.locationPins.size)
        assertEquals(1L, result.locationPins.first().fragmentId)
        assertEquals("上海某地", result.locationPins.first().placeName)
    }

    @Test
    fun invokeCollectsImageUrisFromFragmentsInChronologicalOrder() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val repository = FakeFragmentRepository(
            listOf(
                LifeFragment(
                    id = 1,
                    content = "早",
                    imageUris = listOf("content://early1", "content://early2"),
                    mood = Mood.CALM,
                    tags = emptyList(),
                    createdAt = Instant.parse("2026-05-13T08:00:00Z"),
                    updatedAt = Instant.parse("2026-05-13T08:00:00Z")
                ),
                LifeFragment(
                    id = 2,
                    content = "晚",
                    imageUris = listOf("content://late"),
                    mood = Mood.CALM,
                    tags = emptyList(),
                    createdAt = Instant.parse("2026-05-13T20:00:00Z"),
                    updatedAt = Instant.parse("2026-05-13T20:00:00Z")
                )
            )
        )
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(null),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(UserAppPreferences()),
            NeverCalledAi
        )

        val result = useCase(date, DiaryGenerationMode.RULE_BASED_ONLY)

        assertEquals(
            listOf("content://early1", "content://early2", "content://late"),
            result.imageUris
        )
    }

    @Test
    fun invokeKeepsPriorSavedImageUrisWhenRegenerating() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 1L,
            date = date,
            title = "旧",
            body = "旧正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = listOf("content://old_only"),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "碎片一", Mood.CALM, "2026-05-13T09:00:00Z").copy(
                    imageUris = listOf("content://from_fragment")
                )
            )
        )
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(java.time.ZoneOffset.UTC),
            StaticUserPreferences(UserAppPreferences()),
            NeverCalledAi
        )

        val result = useCase(date, DiaryGenerationMode.RULE_BASED_ONLY)

        assertEquals(listOf("content://old_only", "content://from_fragment"), result.imageUris)
    }

    @Test
    fun addFragmentRejectsCompletelyEmptyInput() = runTest {
        val repository = FakeFragmentRepository(emptyList())
        val useCase = AddFragmentUseCase(repository)

        val result = useCase(content = "   ", imageUris = emptyList(), mood = null, tags = emptyList())

        assertEquals(AddFragmentResult.Empty, result)
        assertTrue(repository.savedFragments.isEmpty())
    }

    private class StaticUserPreferences(
        private val value: UserAppPreferences
    ) : UserPreferencesAccessor {
        override suspend fun current(): UserAppPreferences = value
    }

    private object NeverCalledAi : AiDiaryDraftGenerator {
        override suspend fun generateDraft(
            date: LocalDate,
            fragments: List<LifeFragment>,
            config: LlmConnectionConfig
        ): Result<DiaryDraft> = error("AI must not be used in this test")
    }

    private class StubDiaryRepository(
        private val entry: DiaryEntry?
    ) : DiaryRepository {
        override fun observeDiaries(): Flow<List<DiaryEntry>> = flowOf(emptyList())

        override fun observeDiary(id: Long): Flow<DiaryEntry?> = flowOf(null)

        override suspend fun getDiaryForDate(date: LocalDate): DiaryEntry? =
            entry?.takeIf { it.date == date }

        override suspend fun getDiaryById(id: Long): DiaryEntry? = null

        override suspend fun getAllDiaries(): List<DiaryEntry> = emptyList()

        override suspend fun saveDiary(entry: DiaryEntry): Long = 0L

        override suspend fun deleteDiaryById(id: Long) = Unit
    }

    private class FakeFragmentRepository(
        initialFragments: List<LifeFragment>
    ) : FragmentRepository {
        val savedFragments = mutableListOf<LifeFragment>()
        private val fragments = MutableStateFlow(initialFragments)

        override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> = fragments

        override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> =
            fragments.value.filter { LocalDate.ofInstant(it.createdAt, java.time.ZoneId.systemDefault()) == date }

        override suspend fun getFragmentById(id: Long): LifeFragment? =
            fragments.value.find { it.id == id }

        override suspend fun addFragment(fragment: LifeFragment): Long {
            savedFragments += fragment
            return savedFragments.size.toLong()
        }

        override suspend fun updateFragment(fragment: LifeFragment) {
            fragments.value = fragments.value.map { if (it.id == fragment.id) fragment else it }
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
