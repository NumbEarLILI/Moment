package com.example.moment.ui.diary

import com.example.moment.domain.model.LifeFragment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryPlogTimelineTest {
    @Test
    fun lifeFragmentsForPlogTimeline_usesArchivedTimeForNasGhostFragment() {
        val archivedAt = Instant.parse("2026-05-01T08:30:00Z")
        val ghost = LifeFragment(
            id = 1L,
            stableId = "s1",
            content = "",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = Instant.parse("1970-01-01T00:10:00Z"),
            updatedAt = Instant.parse("1970-01-01T00:10:00Z")
        )

        val fragments = lifeFragmentsForPlogTimeline(
            orderedStableIds = listOf("s1"),
            loadedFragments = listOf(ghost),
            fragmentCreatedAtEpochMillis = mapOf("s1" to archivedAt.toEpochMilli())
        )

        assertEquals(archivedAt, fragments.single().createdAt)
    }

    @Test
    fun lifeFragmentsForPlogTimeline_usesArchivedTimeForMissingNasFragment() {
        val archivedAt = Instant.parse("2026-05-01T09:45:00Z")

        val fragments = lifeFragmentsForPlogTimeline(
            orderedStableIds = listOf("s2"),
            loadedFragments = emptyList(),
            fragmentCreatedAtEpochMillis = mapOf("s2" to archivedAt.toEpochMilli())
        )

        assertEquals(archivedAt, fragments.single().createdAt)
    }
}
