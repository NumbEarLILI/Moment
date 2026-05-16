package com.example.moment.domain.repository

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
}
