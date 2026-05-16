package com.example.moment.domain.repository

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.domain.model.NasArchiveConflictInfo
import com.example.moment.domain.model.NasWebdavConfig

data class NasArchivePushAllResult(
    val diaryCount: Int,
    val imagesUploaded: Int,
    val imagesSkipped: Int
)

data class NasArchivePullResult(
    val diariesApplied: Int,
    val diariesSkipped: Int,
    val imagesRestored: Int
)

/**
 * NAS **存档** 目录 `MomentArchive/`：与 [NasBackupRepository] 的 `MomentBackup/runs/` 快照备份隔离。
 * 按日历日 `.../diaries/<dateEpochDay>/` 维护「当前一篇手帐」的可变副本，用于本机与 NAS 双向同步。
 */
interface NasArchiveRepository {
    suspend fun pushDiaryToArchive(config: NasWebdavConfig, entry: DiaryEntry): Result<Unit>

    suspend fun pushAllDiariesToArchive(config: NasWebdavConfig): Result<NasArchivePushAllResult>

    /**
     * 从 NAS 合并存档；冲突（同日本地更新更晚且正文不一致等）时由 [onConflict] 决定保留本地或使用远端。
     */
    suspend fun pullArchiveToLocal(
        config: NasWebdavConfig,
        onConflict: suspend (NasArchiveConflictInfo) -> NasArchiveConflictChoice
    ): Result<NasArchivePullResult>

    suspend fun deleteArchiveDay(config: NasWebdavConfig, dateEpochDay: Long): Result<Unit>
}
