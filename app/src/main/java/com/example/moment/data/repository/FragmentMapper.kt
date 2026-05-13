package com.example.moment.data.repository

import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.Instant

fun FragmentEntity.toDomain(): LifeFragment = LifeFragment(
    id = id,
    content = content,
    imageUris = imageUris,
    mood = mood?.let(Mood::valueOf),
    tags = tags,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)

fun LifeFragment.toEntity(): FragmentEntity = FragmentEntity(
    id = id,
    content = content,
    imageUris = imageUris,
    mood = mood?.name,
    tags = tags,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)
