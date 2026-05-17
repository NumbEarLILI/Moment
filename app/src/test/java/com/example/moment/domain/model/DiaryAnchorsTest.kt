package com.example.moment.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryAnchorsTest {

    @Test
    fun orderedAnchoredFragmentIds_preservesSourceOrderThenStoriesThenImageKeys() {
        val entry = DiaryEntry(
            date = LocalDate.of(2026, 5, 1),
            title = "t",
            body = "b",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentStableIds = listOf("c", "a"),
            fragmentStories = listOf(FragmentAiStory("z", "s")),
            fragmentImageUris = mapOf("b" to listOf("u")),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertEquals(listOf("c", "a", "z", "b"), orderedAnchoredFragmentIdsForDiary(entry))
    }
}
