package com.example.moment.domain.generator

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedDiaryGeneratorTest {
    private val date: LocalDate = LocalDate.of(2026, 5, 13)
    private val generator = RuleBasedDiaryGenerator(ZoneOffset.UTC)

    @Test
    fun generateReturnsEmptyPromptWhenFragmentsAreEmpty() {
        val draft = generator.generate(date, emptyList())

        assertEquals("还没有记录的一天", draft.title)
        assertTrue(draft.body.contains("今天还没有记录"))
        assertTrue(draft.highlights.isEmpty())
        assertEquals(null, draft.moodSummary)
        assertTrue(draft.fragmentStories.isEmpty())
    }

    @Test
    fun generateIncludesSingleTextFragmentStoryAndShortSummaryBody() {
        val fragment = fragment(
            id = 1,
            content = "早上喝了一杯热咖啡，感觉整个人慢慢醒过来了。",
            mood = Mood.CALM,
            createdAt = "2026-05-13T07:30:00Z"
        )

        val draft = generator.generate(date, listOf(fragment))

        assertEquals("平静的一天", draft.title)
        assertTrue(draft.body.contains("一则瞬间") || draft.body.contains("整体偏平静"))
        assertEquals(
            listOf("07:30 早上喝了一杯热咖啡，感觉整个人慢慢醒过来了。"),
            draft.highlights
        )
        assertEquals("今天整体偏平静。", draft.moodSummary)
        assertEquals(1, draft.fragmentStories.size)
        assertEquals(1L, draft.fragmentStories.first().fragmentId)
        assertTrue(draft.fragmentStories.first().text.contains("热咖啡"))
    }

    @Test
    fun generateOrdersFragmentStoriesChronologically() {
        val fragments = listOf(
            fragment(1, "晚上读了几页书。", Mood.CALM, "2026-05-13T20:10:00Z"),
            fragment(2, "午后完成了拖延很久的小任务。", Mood.HAPPY, "2026-05-13T14:20:00Z"),
            fragment(3, "上午和同事确认了项目方向。", Mood.FOCUSED, "2026-05-13T10:15:00Z")
        )

        val stories = generator.generate(date, fragments).fragmentStories.map { it.fragmentId to it.text }

        assertEquals(listOf(3L, 2L, 1L), stories.map { it.first })
        assertTrue(stories[0].second.contains("项目方向"))
        assertTrue(stories[1].second.contains("小任务"))
        assertTrue(stories[2].second.contains("读了几页书"))
    }

    @Test
    fun generateUsesMostFrequentMoodForSummary() {
        val fragments = listOf(
            fragment(1, "散步时看到很漂亮的晚霞。", Mood.HAPPY, "2026-05-13T18:30:00Z"),
            fragment(2, "买到了喜欢的面包。", Mood.HAPPY, "2026-05-13T09:00:00Z"),
            fragment(3, "睡前整理了一下房间。", Mood.CALM, "2026-05-13T22:00:00Z")
        )

        val draft = generator.generate(date, fragments)

        assertEquals("开心的一天", draft.title)
        assertEquals("今天整体偏开心。", draft.moodSummary)
    }

    @Test
    fun generatePrioritizesTaggedFragmentsInHighlights() {
        val fragments = listOf(
            fragment(1, "这是一条很长很长但是没有标签的普通记录。", Mood.CALM, "2026-05-13T08:30:00Z"),
            fragment(2, "见到老朋友。", Mood.HAPPY, "2026-05-13T19:00:00Z", tags = listOf("朋友"))
        )

        val draft = generator.generate(date, fragments)

        assertTrue(draft.highlights.first().startsWith("19:00 "))
        assertTrue(draft.highlights.first().contains("见到老朋友。"))
    }

    @Test
    fun generateStoryOmitsLocationSuffixFragmentHasLocation() {
        val fragment = fragment(
            id = 1,
            content = "在公园散步。",
            mood = Mood.CALM,
            createdAt = "2026-05-13T16:00:00Z"
        ).copy(
            location = FragmentLocation(
                latitude = 39.9042,
                longitude = 116.4074,
                label = "测试公园"
            )
        )

        val draft = generator.generate(date, listOf(fragment))

        assertEquals("在公园散步。", draft.fragmentStories.single().text)
        assertTrue(!draft.fragmentStories.single().text.contains("测试公园"))
    }

    @Test
    fun generateAppendsNewFragmentsOntoPriorSavedBody() {
        val prior = DiaryEntry(
            id = 1,
            date = date,
            title = "原标题",
            body = "已写好的手帐正文。",
            highlights = listOf("亮点 A"),
            moodSummary = "今天整体偏平静。",
            sourceFragmentIds = listOf(1L),
            imageUris = emptyList(),
            locationPins = emptyList(),
            createdAt = Instant.parse("2026-05-13T07:00:00Z"),
            updatedAt = Instant.parse("2026-05-13T07:00:00Z")
        )
        val fragments = listOf(
            fragment(1, "早上喝了咖啡。", Mood.CALM, "2026-05-13T07:30:00Z"),
            fragment(2, "午后去公园散步。", Mood.HAPPY, "2026-05-13T14:00:00Z")
        )

        val draft = generator.generate(date, fragments, prior)

        assertTrue(draft.body.contains("已写好的手帐正文。"))
        assertTrue(draft.body.contains("新增碎片"))
        assertTrue(draft.body.contains("14:00 午后去公园散步。"))
        assertEquals(listOf(1L, 2L), draft.sourceFragmentIds)
        assertEquals("原标题", draft.title)
        assertEquals(2, draft.fragmentStories.size)
        assertEquals("早上喝了咖啡。", draft.fragmentStories.find { it.fragmentId == 1L }?.text)
        assertEquals("午后去公园散步。", draft.fragmentStories.find { it.fragmentId == 2L }?.text)
    }

    private fun fragment(
        id: Long,
        content: String,
        mood: Mood,
        createdAt: String,
        tags: List<String> = emptyList()
    ): LifeFragment = LifeFragment(
        id = id,
        content = content,
        imageUris = emptyList(),
        mood = mood,
        tags = tags,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(createdAt)
    )
}
