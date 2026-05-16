package com.example.moment.domain.repository

import com.example.moment.domain.model.DiaryEntry
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
     * 读取远端存档；若某日本机在远端 **`diary.json` 的 updatedAt** 更新则跳过，否则覆盖本地该日手帐（与现有备份恢复行为一致）。
     */
    suspend fun pullArchiveToLocal(config: NasWebdavConfig): Result<NasArchivePullResult>

    suspend fun deleteArchiveDay(config: NasWebdavConfig, dateEpochDay: Long): Result<Unit>
}
