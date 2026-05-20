package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.domain.model.NasArchiveConflictInfo
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.NasArchivePullResult
import com.example.moment.domain.repository.NasArchivePushAllResult
import com.example.moment.domain.repository.NasArchiveRepository
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NasArchiveSyncLauncherTest {

    @Test
    fun onDiarySaved_returnsRepositoryFailure() = runTest {
        val expected = IOException("quota exceeded")
        val launcher = NasArchiveSyncLauncher(
            userPreferencesAccessor = StaticPreferencesAccessor(configuredSyncPreferences()),
            nasArchiveRepository = FakeNasArchiveRepository(saveResult = Result.failure(expected))
        )

        val result = launcher.onDiarySaved(diary())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === expected)
    }

    @Test
    fun onDiarySaved_returnsSuccessWhenSyncDisabled() = runTest {
        val launcher = NasArchiveSyncLauncher(
            userPreferencesAccessor = StaticPreferencesAccessor(
                configuredSyncPreferences().copy(nasArchiveSyncEnabled = false)
            ),
            nasArchiveRepository = FakeNasArchiveRepository(saveResult = Result.failure(IOException("unused")))
        )

        val result = launcher.onDiarySaved(diary())

        assertTrue(result.isSuccess)
    }

    private fun configuredSyncPreferences() = UserAppPreferences(
        nasWebdavBaseUrl = "https://nas.example/webdav/",
        nasWebdavUsername = "user",
        nasWebdavPassword = "pass",
        nasArchiveSyncEnabled = true
    )

    private fun diary() = DiaryEntry(
        id = 1,
        date = LocalDate.of(2026, 5, 20),
        title = "标题",
        body = "正文",
        highlights = emptyList(),
        moodSummary = null,
        sourceFragmentStableIds = listOf("s1"),
        createdAt = Instant.parse("2026-05-20T08:00:00Z"),
        updatedAt = Instant.parse("2026-05-20T09:00:00Z")
    )

    private class StaticPreferencesAccessor(
        private val preferences: UserAppPreferences
    ) : UserPreferencesAccessor {
        override suspend fun current(): UserAppPreferences = preferences
    }

    private class FakeNasArchiveRepository(
        private val saveResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : NasArchiveRepository {
        override suspend fun pushDiaryToArchive(config: NasWebdavConfig, entry: DiaryEntry): Result<Unit> =
            saveResult

        override suspend fun pushAllDiariesToArchive(config: NasWebdavConfig): Result<NasArchivePushAllResult> =
            Result.success(NasArchivePushAllResult(diaryCount = 0, imagesUploaded = 0, imagesSkipped = 0))

        override suspend fun pullArchiveToLocal(
            config: NasWebdavConfig,
            onConflict: suspend (NasArchiveConflictInfo) -> NasArchiveConflictChoice
        ): Result<NasArchivePullResult> =
            Result.success(NasArchivePullResult(diariesApplied = 0, diariesSkipped = 0, imagesRestored = 0))

        override suspend fun deleteArchiveDay(config: NasWebdavConfig, dateEpochDay: Long): Result<Unit> =
            deleteResult
    }
}
