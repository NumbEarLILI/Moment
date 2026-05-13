package com.example.moment.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fragments")
data class FragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val imageUris: List<String>,
    val mood: String?,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationLabel: String? = null
)
