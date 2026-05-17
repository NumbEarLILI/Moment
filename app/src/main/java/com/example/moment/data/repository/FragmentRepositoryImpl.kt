package com.example.moment.data.repository

import com.example.moment.data.local.FragmentDao
import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.orderedAnchoredFragmentIdsForDiary
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

    override suspend fun getFragmentsForStableIds(stableIds: List<String>): List<LifeFragment> {
        if (stableIds.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        val ordered = mutableListOf<String>()
        for (s in stableIds) {
            val t = s.trim()
            if (t.isNotEmpty() && seen.add(t)) ordered.add(t)
        }
        if (ordered.isEmpty()) return emptyList()
        val rows = dao.getByStableIds(ordered)
        val byStable = rows.associateBy { it.stableId }
        return ordered.mapNotNull { byStable[it] }.map { it.toDomain() }
    }

    override suspend fun getFragmentById(id: Long): LifeFragment? =
        dao.getById(id)?.toDomain()

    override suspend fun getFragmentByStableId(stableId: String): LifeFragment? {
        val t = stableId.trim()
        if (t.isEmpty()) return null
        return dao.getByStableId(t)?.toDomain()
    }

    override suspend fun addFragment(fragment: LifeFragment): Long =
        dao.insert(fragment.toEntity())

    override suspend fun updateFragment(fragment: LifeFragment) {
        dao.insert(fragment.toEntity())
    }

    override suspend fun deleteFragment(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun ensureGhostPlaceholderFragmentsForDiary(
        entry: DiaryEntry,
        preferredCreatedAtEpochMillisByStableId: Map<String, Long>,
    ) {
        val dayStart = entry.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val ordered = orderedAnchoredFragmentIdsForDiary(entry)
        for ((index, sid) in ordered.withIndex()) {
            val preferred = preferredCreatedAtEpochMillisByStableId[sid]?.takeIf { it > 0L }
            val fallbackMs = dayStart + index * 60_000L
            val existing = dao.getByStableId(sid)
            if (existing != null) {
                val placeholderLike = existing.content.isBlank() && existing.imageUris.isEmpty()
                if (placeholderLike) {
                    val targetMs = when {
                        preferred != null -> preferred
                        !createdAtMillisInDiaryLocalDay(existing.createdAtEpochMillis, entry) -> fallbackMs
                        index > 0 && existing.createdAtEpochMillis < fallbackMs ->
                            fallbackMs
                        else -> null
                    }
                    if (targetMs != null && existing.createdAtEpochMillis != targetMs) {
                        dao.insert(
                            existing.copy(
                                createdAtEpochMillis = targetMs,
                                updatedAtEpochMillis = targetMs,
                            )
                        )
                    }
                }
                continue
            }
            val ms = preferred ?: fallbackMs
            dao.insert(
                FragmentEntity(
                    id = 0,
                    stableId = sid,
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

    private fun createdAtMillisInDiaryLocalDay(createdAtEpochMillis: Long, entry: DiaryEntry): Boolean {
        val start = entry.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = entry.date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return createdAtEpochMillis >= start && createdAtEpochMillis < end
    }

}
