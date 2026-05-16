package com.example.moment.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.moment.domain.model.DiaryLocationPin

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
    val sourceFragmentStableIds: List<String>,
    val imageUris: List<String> = emptyList(),
    val locationPins: List<DiaryLocationPin> = emptyList(),
    val fragmentImageUrisJson: String = "{}",
    val fragmentStoriesJson: String = "[]",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
