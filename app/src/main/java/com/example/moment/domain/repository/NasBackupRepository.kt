package com.example.moment.domain.repository

import com.example.moment.domain.model.NasWebdavConfig

data class NasBackupResult(
    val runFolder: String,
    val diaryCount: Int,
    val imagesUploaded: Int,
    val imagesSkipped: Int
)

interface NasBackupRepository {
    suspend fun testWebDavConnection(config: NasWebdavConfig): Result<Unit>

    /** 在 NAS 上创建 `MomentBackup/runs/<runId>/`，写入 manifest 与各篇日记及图片。 */
    suspend fun backupAllSavedDiaries(config: NasWebdavConfig): Result<NasBackupResult>
}
