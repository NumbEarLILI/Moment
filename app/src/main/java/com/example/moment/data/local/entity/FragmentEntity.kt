package com.example.moment.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fragments")
data class FragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 跨备份/合并可用的业务身份，与自增 [id] 解耦。 */
    val stableId: String,
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
