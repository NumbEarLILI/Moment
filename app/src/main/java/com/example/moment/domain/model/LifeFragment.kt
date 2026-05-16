package com.example.moment.domain.model

import java.time.Instant

data class LifeFragment(
    val id: Long = 0,
    /** 业务身份：手帐时间线与备份引用；与 [id] 解耦。 */
    val stableId: String,
    val content: String,
    val imageUris: List<String>,
    val mood: Mood?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: FragmentLocation? = null
)
