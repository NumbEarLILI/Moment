package com.example.moment.data.repository

import com.example.moment.data.local.DiaryDao
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.repository.DiaryRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiaryRepositoryImpl @Inject constructor(
    private val dao: DiaryDao
) : DiaryRepository {
    override fun observeDiaries(): Flow<List<DiaryEntry>> =
        dao.observeAll().map { entries -> entries.map { it.toDomain() } }

    override fun observeDiary(id: Long): Flow<DiaryEntry?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getDiaryForDate(date: LocalDate): DiaryEntry? =
        dao.getByDate(date.toEpochDay())?.toDomain()

    override suspend fun getDiaryById(id: Long): DiaryEntry? =
        dao.getById(id)?.toDomain()

    override suspend fun getAllDiaries(): List<DiaryEntry> =
        dao.getAll().map { it.toDomain() }

    override suspend fun saveDiary(entry: DiaryEntry): Long =
        dao.upsert(entry.toEntity())

    override suspend fun deleteDiaryById(id: Long) {
        dao.deleteById(id)
    }
}
