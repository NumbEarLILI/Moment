package com.example.moment.domain.usecase

import com.example.moment.domain.diary.AiDiarySynthesizer
import com.example.moment.domain.diary.DiaryDraftComposer
import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.repository.FragmentRepository
import com.example.moment.domain.repository.UserPreferencesRepository
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
        val useCase = defaultGenerateDiaryDraftUseCase(repository)

        val result = useCase(date)

        assertTrue(result.body.contains("今天午后完成了拖延很久的小任务"))
        assertTrue(result.sourceFragmentIds.contains(1))
        assertTrue(!result.sourceFragmentIds.contains(2))
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
        val useCase = defaultGenerateDiaryDraftUseCase(repository)

        val result = useCase(date)

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
        val useCase = defaultGenerateDiaryDraftUseCase(repository)

        val result = useCase(date)

        assertEquals(
            listOf("content://early1", "content://early2", "content://late"),
            result.imageUris
        )
    }

    @Test
    fun invokeUsesAiDraftWhenModelConfigured() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val repository = FakeFragmentRepository(
            listOf(fragment(1, "手帐来源句", Mood.HAPPY, "2026-05-13T09:00:00Z"))
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://api.x/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val aiDraft = DiaryDraft(
            title = "云端标题",
            body = "云端正文",
            highlights = listOf("亮"),
            moodSummary = "上扬",
            sourceFragmentIds = listOf(1L)
        )
        val ai = object : AiDiarySynthesizer {
            override suspend fun synthesize(
                preferences: UserAppPreferences,
                d: LocalDate,
                fragments: List<LifeFragment>
            ): DiaryDraft {
                assertEquals(prefs.aiModel, preferences.aiModel)
                return aiDraft
            }
        }
        val useCase = defaultGenerateDiaryDraftUseCase(repository, prefs, ai)
        val result = useCase(date)
        assertEquals("云端标题", result.title)
        assertEquals("云端正文", result.body)
        assertEquals(listOf(1L), result.sourceFragmentIds)
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

    private fun defaultGenerateDiaryDraftUseCase(
        repository: FragmentRepository,
        prefs: UserAppPreferences = UserAppPreferences(),
        ai: AiDiarySynthesizer = object : AiDiarySynthesizer {
            override suspend fun synthesize(
                preferences: UserAppPreferences,
                date: LocalDate,
                fragments: List<LifeFragment>
            ): DiaryDraft = error("unexpected_ai")
        }
    ): GenerateDiaryDraftUseCase {
        val composer = DiaryDraftComposer(RuleBasedDiaryGenerator(), ai)
        return GenerateDiaryDraftUseCase(
            fragmentRepository = repository,
            userPreferencesRepository = FakeUserPreferencesRepository(prefs),
            diaryDraftComposer = composer
        )
    }

    private class FakeUserPreferencesRepository(
        initial: UserAppPreferences = UserAppPreferences()
    ) : UserPreferencesRepository {
        private val backing = MutableStateFlow(initial)
        override val preferences: Flow<UserAppPreferences> = backing
        override suspend fun setThemeMode(mode: AppThemeMode) {
            backing.value = backing.value.copy(themeMode = mode)
        }
        override suspend fun setAiSettings(baseUrl: String, apiKey: String, model: String) {
            backing.value = backing.value.copy(aiBaseUrl = baseUrl, aiApiKey = apiKey, aiModel = model)
        }
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
