package com.example.moment.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.moment.data.local.entity.FragmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FragmentDao {
    @Query(
        """
        SELECT * FROM fragments
        WHERE createdAtEpochMillis >= :startInclusive
          AND createdAtEpochMillis < :endExclusive
        ORDER BY createdAtEpochMillis DESC
        """
    )
    fun observeForRange(startInclusive: Long, endExclusive: Long): Flow<List<FragmentEntity>>

    @Query(
        """
        SELECT * FROM fragments
        WHERE createdAtEpochMillis >= :startInclusive
          AND createdAtEpochMillis < :endExclusive
        ORDER BY createdAtEpochMillis ASC
        """
    )
    suspend fun getForRange(startInclusive: Long, endExclusive: Long): List<FragmentEntity>

    @Query("SELECT * FROM fragments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FragmentEntity?

    @Query("SELECT * FROM fragments WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FragmentEntity>

    @Query("SELECT * FROM fragments WHERE stableId = :stableId LIMIT 1")
    suspend fun getByStableId(stableId: String): FragmentEntity?

    @Query("SELECT * FROM fragments WHERE stableId IN (:stableIds)")
    suspend fun getByStableIds(stableIds: List<String>): List<FragmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fragment: FragmentEntity): Long

    @Query("DELETE FROM fragments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
