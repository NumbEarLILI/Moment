package com.example.moment.domain.repository

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface FragmentRepository {
    fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>>
    suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment>
    /** Plog UI: load fragments by the diary/draft's stored ids (not restricted to calendar day). */
    suspend fun getFragmentsForSourceIds(sourceFragmentIds: List<Long>): List<LifeFragment>
    suspend fun getFragmentById(id: Long): LifeFragment?
    suspend fun addFragment(fragment: LifeFragment): Long
    suspend fun updateFragment(fragment: LifeFragment)
    suspend fun deleteFragment(id: Long)

    /**
     * NAS 等场景仅恢复手帐、无碎片表行时，备份里的 [DiaryEntry.sourceFragmentIds] 可能与本地自增 id 冲突。
     * 为手帐锚点 id 插入占位行（时间戳不在真实日历日内），占用 id，详见 [com.example.moment.data.nas.NasBackupRepositoryImpl]。
     */
    suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry)
}
