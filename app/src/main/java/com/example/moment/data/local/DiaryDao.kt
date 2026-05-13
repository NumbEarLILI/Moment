package com.example.moment.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.moment.data.local.entity.DiaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries WHERE id = :id")
    fun observeById(id: Long): Flow<DiaryEntity?>

    @Query("SELECT * FROM diaries WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getByDate(dateEpochDay: Long): DiaryEntity?

    @Query("SELECT * FROM diaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiaryEntity): Long
}
