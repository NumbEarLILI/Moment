package com.example.moment.domain.repository

import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface FragmentRepository {
    fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>>
    suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment>
    /** Plog UI：按手帐保存的 stableId 顺序加载（不按日历日过滤）。 */
    suspend fun getFragmentsForStableIds(stableIds: List<String>): List<LifeFragment>
    suspend fun getFragmentById(id: Long): LifeFragment?
    suspend fun getFragmentByStableId(stableId: String): LifeFragment?
    suspend fun addFragment(fragment: LifeFragment): Long
    suspend fun updateFragment(fragment: LifeFragment)
    suspend fun deleteFragment(id: Long)

    /**
     * NAS 恢复等：手帐已含 stableId，本地尚无对应行时插入占位（时间戳远离真实日历日）。
     */
    suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry)
}
