package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryLocationPin
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NasBackupJsonModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun diaryFileDto_roundTrips() {
        val original = NasBackupDiaryFileDto(
            id = 3L,
            dateEpochDay = LocalDate.of(2026, 5, 1).toEpochDay(),
            title = "标题",
            body = "正文",
            highlights = listOf("a", "b"),
            moodSummary = "还行",
            sourceFragmentIds = listOf(1L, 2L),
            imageRelativePaths = listOf("images/0.bin", null),
            locationPins = listOf(
                DiaryLocationPin(1L, "家", 30.0, 120.0)
            ),
            createdAtEpochMillis = Instant.parse("2026-05-01T10:00:00Z").toEpochMilli(),
            updatedAtEpochMillis = Instant.parse("2026-05-01T11:00:00Z").toEpochMilli()
        )
        val encoded = json.encodeToString(NasBackupDiaryFileDto.serializer(), original)
        val decoded = json.decodeFromString(NasBackupDiaryFileDto.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
