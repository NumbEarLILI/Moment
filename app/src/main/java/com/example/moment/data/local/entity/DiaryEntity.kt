package com.example.moment.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diaries",
    indices = [Index(value = ["dateEpochDay"], unique = true)]
)
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
