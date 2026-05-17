package com.example.moment.data.repository

import com.example.moment.domain.model.DiaryEntry
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryMapperTest {
    @Test
    fun diaryEntryRoundTrip_preservesFragmentCreatedAtEpochMillis() {
        val entry = DiaryEntry(
            id = 7L,
            date = LocalDate.of(2026, 5, 1),
            title = "标题",
            body = "正文",
            highlights = emptyList(),
            moodSummary = null,
            sourceFragmentStableIds = listOf("s1", "s2"),
            fragmentCreatedAtEpochMillis = mapOf(
                "s1" to Instant.parse("2026-05-01T08:30:00Z").toEpochMilli(),
                "s2" to Instant.parse("2026-05-01T09:45:00Z").toEpochMilli()
            ),
            createdAt = Instant.parse("2026-05-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-05-01T11:00:00Z")
        )

        val roundTripped = entry.toEntity().toDomain()

        assertEquals(entry.fragmentCreatedAtEpochMillis, roundTripped.fragmentCreatedAtEpochMillis)
    }
}
