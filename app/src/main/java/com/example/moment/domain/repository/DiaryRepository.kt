package com.example.moment.domain.repository

import com.example.moment.domain.model.DiaryEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    fun observeDiaries(): Flow<List<DiaryEntry>>
    fun observeDiary(id: Long): Flow<DiaryEntry?>
    suspend fun getDiaryForDate(date: LocalDate): DiaryEntry?
    suspend fun saveDiary(entry: DiaryEntry): Long
}
