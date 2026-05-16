package com.example.moment.data.repository

import com.example.moment.data.local.FragmentDao
import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.anchoredFragmentIds
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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

    override suspend fun ensureGhostPlaceholderFragmentsForDiary(entry: DiaryEntry) {
        val base = GHOST_PLACEHOLDER_EPOCH_MS
        for (id in entry.anchoredFragmentIds().sorted()) {
            if (dao.getById(id) != null) continue
            val ms = base + id
            dao.insert(
                FragmentEntity(
                    id = id,
                    content = "",
                    imageUris = emptyList(),
                    mood = null,
                    tags = emptyList(),
                    createdAtEpochMillis = ms,
                    updatedAtEpochMillis = ms,
                    locationLatitude = null,
                    locationLongitude = null,
                    locationLabel = null
                )
            )
        }
    }

    private fun dateRange(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    private companion object {
        /** 占位碎片时间：远离真实记录日，避免出现在「某日碎片」列表里，仅占位主键 id。 */
        val GHOST_PLACEHOLDER_EPOCH_MS: Long =
            LocalDate.of(1970, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
