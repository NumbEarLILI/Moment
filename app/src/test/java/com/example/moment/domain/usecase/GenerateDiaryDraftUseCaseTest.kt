package com.example.moment.domain.usecase

import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.llm.AiDiaryDraftGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentAiStory
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

        assertTrue(result.fragmentStories.any { it.text.contains("今天午后完成了拖延很久的小任务") })
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
            body = "AI 总述",
            highlights = listOf("AI 亮点"),
            moodSummary = "开心",
            sourceFragmentIds = listOf(99L),
            fragmentStories = listOf(FragmentAiStory(1L, "AI 为这一则写的短文。"))
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> {
                assertEquals("https://example.com/v1", config.baseUrl)
                assertEquals("k", config.apiKey)
                assertEquals("m", config.model)
                assertEquals(1, fragments.size)
                assertEquals(null, priorSavedDiary)
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
        assertEquals("AI 总述", result.body)
        assertEquals(listOf(1L), result.sourceFragmentIds)
        assertEquals(listOf(FragmentAiStory(1L, "AI 为这一则写的短文。")), result.fragmentStories)
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
    fun invokeKeepsPriorSavedDiaryImageUrisWhenRegenerating() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 1,
            date = date,
            title = "旧",
            body = "旧正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = listOf("content://from-saved-diary", "content://early1"),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
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
                )
            )
        )
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(UserAppPreferences()),
            NeverCalledAi
        )

        val result = useCase(date, DiaryGenerationMode.RULE_BASED_ONLY)

        assertEquals(
            listOf("content://early1", "content://early2", "content://from-saved-diary"),
            result.imageUris
        )
    }

    @Test
    fun invokeMergesPriorSavedDiaryWhenRuleBasedAndNewFragmentsExist() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 1,
            date = date,
            title = "旧标题",
            body = "已保存的手帐正文。",
            highlights = listOf("旧亮点"),
            moodSummary = "今天整体偏平静。",
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "早上喝了咖啡。", Mood.CALM, "2026-05-13T07:30:00Z"),
                fragment(2, "晚上吃了火锅。", Mood.HAPPY, "2026-05-13T19:00:00Z")
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

        assertTrue(result.body.contains("已保存的手帐正文。"))
        assertTrue(result.body.contains("新增碎片"))
        assertTrue(result.body.contains("19:00") && result.body.contains("火锅"))
        assertEquals(listOf(1L, 2L), result.sourceFragmentIds)
    }

    @Test
    fun invokePassesPriorSavedDiaryToAiWhenPresent() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "已保存",
            body = "底稿",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "旧碎片。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "新碎片。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "合并后",
            body = "合并正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(2L),
            fragmentStories = emptyList()
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> {
                assertEquals(prior, priorSavedDiary)
                assertEquals(2, fragments.size)
                return Result.success(outDraft)
            }
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertEquals("合并后", result.title)
        assertTrue(result.body.contains("底稿"))
        assertTrue(result.body.contains("合并正文"))
        assertEquals(listOf(1L, 2L), result.sourceFragmentIds)
        assertEquals("旧碎片。", result.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("新碎片。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
    }

    @Test
    fun invokeMergesPriorWhenSourceIdsAlreadyCoverAllFragments() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "原标题",
            body = "旧稿整体段落。",
            highlights = listOf("旧亮"),
            moodSummary = "平静",
            sourceFragmentIds = listOf(1L, 2L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            fragmentStories = listOf(
                FragmentAiStory(1L, "条1 旧逐条。"),
                FragmentAiStory(2L, "条2 旧逐条。")
            ),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "一。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "二。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "模型新标题",
            body = "模型只写了这一段。",
            highlights = listOf("新亮"),
            moodSummary = "兴奋",
            sourceFragmentIds = emptyList(),
            fragmentStories = listOf(
                FragmentAiStory(1L, "不该采用。"),
                FragmentAiStory(2L, "不该采用2。")
            )
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> = Result.success(outDraft)
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertTrue(result.body.contains("旧稿整体段落。"))
        assertTrue(result.body.contains("条1 旧逐条。"))
        assertTrue(result.body.contains("条2 旧逐条。"))
        // 无新增碎片时不叠模型段落，避免重复打开预览导致正文雪球
        assertTrue(!result.body.contains("模型只写了这一段。"))
        assertEquals("条1 旧逐条。", result.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("条2 旧逐条。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
        assertTrue(result.highlights.contains("旧亮"))
        assertTrue(result.highlights.contains("新亮"))
    }

    @Test
    fun invokeKeepsPriorBodyWhenAiReturnsEmptyBodyAndNewFragmentsExist() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "已保存",
            body = "底稿正文不要丢。",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "旧碎片。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "新碎片。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "仅标题",
            body = "",
            highlights = listOf("AI亮点"),
            moodSummary = "开心",
            sourceFragmentIds = listOf(2L),
            fragmentStories = emptyList()
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> = Result.success(outDraft)
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertEquals("底稿正文不要丢。", result.body)
        assertTrue(result.highlights.contains("AI亮点"))
        assertEquals("新碎片。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
    }

    @Test
    fun invokeMergesAiWithPriorNarrativeFromFragmentStoriesWhenBodyBlank() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "已保存",
            body = "",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            fragmentStories = listOf(FragmentAiStory(1L, "时间线上的旧稿长文。")),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "短。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "新碎片。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "新",
            body = "",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = emptyList(),
            fragmentStories = emptyList()
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> = Result.success(outDraft)
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertTrue(result.body.contains("时间线上的旧稿长文。"))
        assertEquals("时间线上的旧稿长文。", result.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("新碎片。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
    }

    @Test
    fun invokeMergesAiWithPriorShortBodyPlusFragmentStories() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "已保存",
            body = "短总述。",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            fragmentStories = listOf(FragmentAiStory(1L, "时间线上的旧稿长文。")),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "短。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "新碎片。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "新",
            body = "模型补充段落。",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = emptyList(),
            fragmentStories = listOf(FragmentAiStory(2L, "新碎片的 AI。"))
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> = Result.success(outDraft)
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertTrue(result.body.contains("短总述。"))
        assertTrue(result.body.contains("时间线上的旧稿长文。"))
        assertTrue(result.body.contains("模型补充段落。"))
        assertEquals("时间线上的旧稿长文。", result.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("新碎片的 AI。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
    }

    @Test
    fun invokePreservesPriorFragmentStoryWhenAiOverwritesOldFragment() = runTest {
        val date = LocalDate.of(2026, 5, 13)
        val prior = DiaryEntry(
            id = 9,
            date = date,
            title = "已保存",
            body = "底稿",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            fragmentStories = listOf(FragmentAiStory(1L, "必须保留的旧逐条全文。")),
            createdAt = Instant.parse("2026-05-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T08:00:00Z")
        )
        val repository = FakeFragmentRepository(
            listOf(
                fragment(1, "短。", Mood.CALM, "2026-05-13T08:00:00Z"),
                fragment(2, "新碎片。", Mood.HAPPY, "2026-05-13T18:00:00Z")
            )
        )
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://example.com/v1",
            aiApiKey = "k",
            aiModel = "m"
        )
        val outDraft = DiaryDraft(
            title = "新",
            body = "AI 正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentIds = emptyList(),
            fragmentStories = listOf(
                FragmentAiStory(1L, "模型错误缩写的旧条。"),
                FragmentAiStory(2L, "新条。")
            )
        )
        val fakeAi = object : AiDiaryDraftGenerator {
            override suspend fun generateDraft(
                date: LocalDate,
                fragments: List<LifeFragment>,
                config: LlmConnectionConfig,
                priorSavedDiary: DiaryEntry?
            ): Result<DiaryDraft> = Result.success(outDraft)
        }
        val useCase = GenerateDiaryDraftUseCase(
            repository,
            StubDiaryRepository(prior),
            RuleBasedDiaryGenerator(),
            StaticUserPreferences(prefs),
            fakeAi
        )

        val result = useCase(date, DiaryGenerationMode.AUTO)

        assertEquals("必须保留的旧逐条全文。", result.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("新条。", result.fragmentStories.find { it.fragmentId == 2L }?.text)
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
            config: LlmConnectionConfig,
            priorSavedDiary: DiaryEntry?
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
