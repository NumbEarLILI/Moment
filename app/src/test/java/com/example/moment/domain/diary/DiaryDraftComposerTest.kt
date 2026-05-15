package com.example.moment.domain.diary

import com.example.moment.domain.generator.RuleBasedDiaryGenerator
import com.example.moment.domain.model.DiaryDraft
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.UserAppPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryDraftComposerTest {

    private val date = LocalDate.of(2026, 5, 13)
    private val fragments = listOf(
        LifeFragment(
            id = 1,
            content = "午后写代码",
            imageUris = emptyList(),
            mood = Mood.FOCUSED,
            tags = emptyList(),
            createdAt = Instant.parse("2026-05-13T14:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T14:00:00Z")
        )
    )

    @Test
    fun composeUsesRuleBasedWhenAiNotConfigured() = runTest {
        val composer = DiaryDraftComposer(
            RuleBasedDiaryGenerator(ZoneOffset.UTC),
            object : AiDiarySynthesizer {
                override suspend fun synthesize(
                    preferences: UserAppPreferences,
                    date: LocalDate,
                    fragments: List<LifeFragment>
                ): DiaryDraft = error("ai_should_not_run")
            }
        )
        val draft = composer.compose(date, fragments, UserAppPreferences())
        assertTrue(draft.body.contains("午后写代码"))
    }

    @Test
    fun composeUsesAiWhenConfigured() = runTest {
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://api.example/v1",
            aiApiKey = "secret",
            aiModel = "test-model"
        )
        val expected = DiaryDraft(
            title = "模型标题",
            body = "模型正文",
            highlights = listOf("点"),
            moodSummary = "稳",
            sourceFragmentIds = listOf(1L)
        )
        val composer = DiaryDraftComposer(
            RuleBasedDiaryGenerator(ZoneOffset.UTC),
            object : AiDiarySynthesizer {
                override suspend fun synthesize(
                    preferences: UserAppPreferences,
                    d: LocalDate,
                    frags: List<LifeFragment>
                ): DiaryDraft {
                    assertEquals(prefs.aiModel, preferences.aiModel)
                    assertEquals(1, frags.size)
                    return expected
                }
            }
        )
        val draft = composer.compose(date, fragments, prefs)
        assertEquals(expected.title, draft.title)
        assertEquals(expected.body, draft.body)
    }

    @Test
    fun composeFallsBackWhenAiThrows() = runTest {
        val prefs = UserAppPreferences(
            aiBaseUrl = "https://api.example/v1",
            aiApiKey = "secret",
            aiModel = "test-model"
        )
        val composer = DiaryDraftComposer(
            RuleBasedDiaryGenerator(ZoneOffset.UTC),
            object : AiDiarySynthesizer {
                override suspend fun synthesize(
                    preferences: UserAppPreferences,
                    date: LocalDate,
                    fragments: List<LifeFragment>
                ): DiaryDraft = error("network_down")
            }
        )
        val draft = composer.compose(date, fragments, prefs)
        assertTrue(draft.body.contains("午后写代码"))
    }
}
