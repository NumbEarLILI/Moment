package com.example.moment.ui.diary

import com.example.moment.domain.model.LifeFragment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiaryPlogTimeEditTest {

    @Test
    fun updatePlogTime_updatesTextEpochMapAndDisplayedFragmentTime() {
        val state = DiaryEditorUiState(
            date = LocalDate.of(2026, 5, 20),
            sourceFragmentStableIds = listOf("s1"),
            plogFragments = listOf(fragment("s1", Instant.parse("2026-05-20T08:00:00Z"))),
            fragmentCreatedAtEpochMillis = mapOf("s1" to Instant.parse("2026-05-20T08:00:00Z").toEpochMilli()),
            plogTimeTexts = mapOf("s1" to "08:00")
        )

        val updated = updatePlogTimeText(
            state = state,
            stableId = "s1",
            timeText = "09:30",
            zoneId = ZoneOffset.UTC
        )

        val expected = Instant.parse("2026-05-20T09:30:00Z")
        assertNull(updated.errorMessage)
        assertEquals("09:30", updated.plogTimeTexts["s1"])
        assertEquals(expected.toEpochMilli(), updated.fragmentCreatedAtEpochMillis["s1"])
        assertEquals(expected, updated.plogFragments.single().createdAt)
    }

    @Test
    fun updatePlogTime_keepsInvalidTextButDoesNotOverwriteLastValidEpoch() {
        val original = Instant.parse("2026-05-20T08:00:00Z")
        val state = DiaryEditorUiState(
            date = LocalDate.of(2026, 5, 20),
            sourceFragmentStableIds = listOf("s1"),
            plogFragments = listOf(fragment("s1", original)),
            fragmentCreatedAtEpochMillis = mapOf("s1" to original.toEpochMilli()),
            plogTimeTexts = mapOf("s1" to "08:00")
        )

        val updated = updatePlogTimeText(state, "s1", "25:99", ZoneOffset.UTC)

        assertEquals("25:99", updated.plogTimeTexts["s1"])
        assertEquals(original.toEpochMilli(), updated.fragmentCreatedAtEpochMillis["s1"])
        assertEquals(original, updated.plogFragments.single().createdAt)
        assertNotNull(updated.errorMessage)
    }

    private fun fragment(stableId: String, createdAt: Instant) = LifeFragment(
        id = 1L,
        stableId = stableId,
        content = "fragment",
        imageUris = emptyList(),
        mood = null,
        tags = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
