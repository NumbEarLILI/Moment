package com.example.moment.domain.model

import java.time.Instant

data class LifeFragment(
    val id: Long = 0,
    val content: String,
    val imageUris: List<String>,
    val mood: Mood?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: FragmentLocation? = null
)
