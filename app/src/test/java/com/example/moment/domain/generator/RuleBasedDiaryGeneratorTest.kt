package com.example.moment.domain.generator

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedDiaryGeneratorTest {
    private val date: LocalDate = LocalDate.of(2026, 5, 13)

    @Test
    fun generateReturnsEmptyPromptWhenFragmentsAreEmpty() {
        val draft = RuleBasedDiaryGenerator().generate(date, emptyList())

        assertEquals("还没有记录的一天", draft.title)
        assertTrue(draft.body.contains("今天还没有记录"))
        assertTrue(draft.highlights.isEmpty())
        assertEquals(null, draft.moodSummary)
    }

    @Test
    fun generateIncludesSingleTextFragmentInBodyAndHighlight() {
        val fragment = fragment(
            id = 1,
            content = "早上喝了一杯热咖啡，感觉整个人慢慢醒过来了。",
            mood = Mood.CALM,
            createdAt = "2026-05-13T07:30:00Z"
        )

        val draft = RuleBasedDiaryGenerator().generate(date, listOf(fragment))

        assertEquals("平静的一天", draft.title)
        assertTrue(draft.body.contains("清晨"))
        assertTrue(draft.body.contains("早上喝了一杯热咖啡"))
        assertEquals(listOf("早上喝了一杯热咖啡，感觉整个人慢慢醒过来了。"), draft.highlights)
        assertEquals("今天整体偏平静。", draft.moodSummary)
    }

    @Test
    fun generateOrdersMultipleTimeBucketsChronologically() {
        val fragments = listOf(
            fragment(1, "晚上读了几页书。", Mood.CALM, "2026-05-13T20:10:00Z"),
            fragment(2, "午后完成了拖延很久的小任务。", Mood.HAPPY, "2026-05-13T14:20:00Z"),
            fragment(3, "上午和同事确认了项目方向。", Mood.FOCUSED, "2026-05-13T10:15:00Z")
        )

        val body = RuleBasedDiaryGenerator().generate(date, fragments).body

        assertTrue(body.indexOf("上午") < body.indexOf("午后"))
        assertTrue(body.indexOf("午后") < body.indexOf("夜晚"))
    }

    @Test
    fun generateUsesMostFrequentMoodForSummary() {
        val fragments = listOf(
            fragment(1, "散步时看到很漂亮的晚霞。", Mood.HAPPY, "2026-05-13T18:30:00Z"),
            fragment(2, "买到了喜欢的面包。", Mood.HAPPY, "2026-05-13T09:00:00Z"),
            fragment(3, "睡前整理了一下房间。", Mood.CALM, "2026-05-13T22:00:00Z")
        )

        val draft = RuleBasedDiaryGenerator().generate(date, fragments)

        assertEquals("开心的一天", draft.title)
        assertEquals("今天整体偏开心。", draft.moodSummary)
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
