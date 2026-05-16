package com.example.moment.domain.nas

import com.example.moment.domain.model.DiaryEntry

/**
 * 手帐持久化后的 NAS 存档侧效应（与 [com.example.moment.domain.repository.NasBackupRepository] 快照备份无关）。
 */
interface NasArchiveSyncCoordinator {
    suspend fun onDiarySaved(entry: DiaryEntry)
    suspend fun onDiaryDeleted(dateEpochDay: Long)
}
