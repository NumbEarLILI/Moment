package com.example.moment.domain.repository

import com.example.moment.domain.model.LifeFragment
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface FragmentRepository {
    fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>>
    suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment>
    suspend fun addFragment(fragment: LifeFragment): Long
    suspend fun deleteFragment(id: Long)
}
