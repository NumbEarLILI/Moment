package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryEntry
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasArchiveRestorePolicyTest {

    @Test
    fun newerLocalDifferentDiary_isNotRestoredFromOlderBackup() {
        val local = diary(
            body = "本地新正文",
            updatedAt = Instant.parse("2026-05-20T10:00:00Z")
        )
        val remote = dto(
            body = "备份旧正文",
            updatedAt = Instant.parse("2026-05-19T10:00:00Z")
        )

        val shouldRestore = shouldRestoreNasBackupDiary(
            existing = local,
            dto = remote,
            contentMatches = false
        )

        assertFalse(shouldRestore)
    }

    @Test
    fun newerLocalSameDiary_canRefreshMissingImagesFromBackup() {
        val local = diary(updatedAt = Instant.parse("2026-05-20T10:00:00Z"))
        val remote = dto(updatedAt = Instant.parse("2026-05-19T10:00:00Z"))

        val shouldRestore = shouldRestoreNasBackupDiary(
            existing = local,
            dto = remote,
            contentMatches = true
        )

        assertTrue(shouldRestore)
    }

    @Test
    fun newerRemoteDiary_isRestored() {
        val local = diary(updatedAt = Instant.parse("2026-05-19T10:00:00Z"))
        val remote = dto(updatedAt = Instant.parse("2026-05-20T10:00:00Z"))

        val shouldRestore = shouldRestoreNasBackupDiary(
            existing = local,
            dto = remote,
            contentMatches = false
        )

        assertTrue(shouldRestore)
    }

    private fun diary(
        body: String = "正文",
        updatedAt: Instant
    ) = DiaryEntry(
        id = 1,
        date = LocalDate.of(2026, 5, 20),
        title = "标题",
        body = body,
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = listOf("s1"),
        createdAt = Instant.parse("2026-05-20T08:00:00Z"),
        updatedAt = updatedAt
    )

    private fun dto(
        body: String = "正文",
        updatedAt: Instant
    ) = NasBackupDiaryFileDto(
        schemaVersion = 3,
        id = 1,
        dateEpochDay = LocalDate.of(2026, 5, 20).toEpochDay(),
        title = "标题",
        body = body,
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = listOf("s1"),
        imageRelativePaths = emptyList(),
        createdAtEpochMillis = Instant.parse("2026-05-20T08:00:00Z").toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli()
    )
}
