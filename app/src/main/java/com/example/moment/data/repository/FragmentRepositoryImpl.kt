package com.example.moment.data.repository

import com.example.moment.data.local.FragmentDao
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FragmentRepositoryImpl @Inject constructor(
    private val dao: FragmentDao,
    private val zoneId: ZoneId
) : FragmentRepository {
    override fun observeFragmentsForDate(date: LocalDate): Flow<List<LifeFragment>> {
        val (start, end) = dateRange(date)
        return dao.observeForRange(start, end).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getFragmentsForDate(date: LocalDate): List<LifeFragment> {
        val (start, end) = dateRange(date)
        return dao.getForRange(start, end).map { it.toDomain() }
    }

    override suspend fun getFragmentsForSourceIds(sourceFragmentIds: List<Long>): List<LifeFragment> {
        if (sourceFragmentIds.isEmpty()) return emptyList()
        val seen = linkedSetOf<Long>()
        val orderedIds = mutableListOf<Long>()
        for (id in sourceFragmentIds) {
            if (id > 0L && seen.add(id)) orderedIds.add(id)
        }
        val byId = dao.getByIds(orderedIds).associateBy { it.id }
        return orderedIds.mapNotNull { byId[it] }.map { it.toDomain() }
    }

    override suspend fun getFragmentById(id: Long): LifeFragment? =
        dao.getById(id)?.toDomain()

    override suspend fun addFragment(fragment: LifeFragment): Long =
        dao.insert(fragment.toEntity())

    override suspend fun updateFragment(fragment: LifeFragment) {
        dao.insert(fragment.toEntity())
    }

    override suspend fun deleteFragment(id: Long) {
        dao.deleteById(id)
    }

    private fun dateRange(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }
}
