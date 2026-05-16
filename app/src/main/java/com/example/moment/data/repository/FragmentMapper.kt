package com.example.moment.data.repository

import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.Instant

fun FragmentEntity.toDomain(): LifeFragment = LifeFragment(
    id = id,
    stableId = stableId,
    content = content,
    imageUris = imageUris,
    mood = mood?.let(Mood::valueOf),
    tags = tags,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    location = locationOrNull()
)

private fun FragmentEntity.locationOrNull(): FragmentLocation? {
    val lat = locationLatitude ?: return null
    val lng = locationLongitude ?: return null
    return FragmentLocation(latitude = lat, longitude = lng, label = locationLabel)
}

fun LifeFragment.toEntity(): FragmentEntity = FragmentEntity(
    id = id,
    stableId = stableId,
    content = content,
    imageUris = imageUris,
    mood = mood?.name,
    tags = tags,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    locationLatitude = location?.latitude,
    locationLongitude = location?.longitude,
    locationLabel = location?.label
)
