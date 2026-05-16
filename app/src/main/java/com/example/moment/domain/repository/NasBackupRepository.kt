package com.example.moment.domain.repository

import com.example.moment.domain.model.NasWebdavConfig

data class NasBackupResult(
    val runFolder: String,
    val diaryCount: Int,
    val imagesUploaded: Int,
    val imagesSkipped: Int
)

data class NasRestoreResult(
    val runId: String,
    val diariesRestored: Int,
    val imagesRestored: Int,
    val diariesSkipped: Int
)

interface NasBackupRepository {
    suspend fun testWebDavConnection(config: NasWebdavConfig): Result<Unit>

    /** 在 NAS 上创建 `MomentBackup/runs/<runId>/`，写入 manifest 与各篇日记及图片。 */
    suspend fun backupAllSavedDiaries(config: NasWebdavConfig): Result<NasBackupResult>

    /** 列出 `MomentBackup/runs/` 下的 `run_*` 目录名（新在前）。 */
    suspend fun listBackupRuns(config: NasWebdavConfig): Result<List<String>>

    /** 从指定备份目录恢复手帐到本地（按日期 upsert；图片写入应用私有目录）。 */
    suspend fun restoreBackupRun(config: NasWebdavConfig, runId: String): Result<NasRestoreResult>

    /** 删除 NAS 上 `MomentBackup/runs/<runId>/` 整棵目录（递归）。 */
    suspend fun deleteBackupRun(config: NasWebdavConfig, runId: String): Result<Unit>
}
