package com.example.moment.data.nas

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.toNasWebdavConfig
import com.example.moment.domain.nas.NasArchiveSyncCoordinator
import com.example.moment.domain.preferences.UserPreferencesAccessor
import com.example.moment.domain.repository.NasArchiveRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 手帐本地写入后的 NAS 存档推送；与「快照备份」流程隔离。 */
@Singleton
class NasArchiveSyncLauncher @Inject constructor(
    private val userPreferencesAccessor: UserPreferencesAccessor,
    private val nasArchiveRepository: NasArchiveRepository
) : NasArchiveSyncCoordinator {
    override suspend fun onDiarySaved(entry: DiaryEntry) {
        val p = userPreferencesAccessor.current()
        if (!p.nasArchiveSyncEnabled) return
        val cfg = p.toNasWebdavConfig()
        if (!cfg.isConfigured()) return
        nasArchiveRepository.pushDiaryToArchive(cfg, entry)
    }

    override suspend fun onDiaryDeleted(dateEpochDay: Long) {
        val p = userPreferencesAccessor.current()
        if (!p.nasArchiveSyncEnabled) return
        val cfg = p.toNasWebdavConfig()
        if (!cfg.isConfigured()) return
        nasArchiveRepository.deleteArchiveDay(cfg, dateEpochDay)
    }
}
