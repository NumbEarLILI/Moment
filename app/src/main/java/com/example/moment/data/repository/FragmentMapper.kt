package com.example.moment.data.repository

import com.example.moment.data.local.entity.FragmentEntity
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
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
    location = locationOrNull(),
    weather = weatherOrNull()
)

private fun FragmentEntity.locationOrNull(): FragmentLocation? {
    val lat = locationLatitude ?: return null
    val lng = locationLongitude ?: return null
    return FragmentLocation(latitude = lat, longitude = lng, label = locationLabel)
}

private fun FragmentEntity.weatherOrNull(): FragmentWeather? {
    val condition = weatherCondition?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val temperature = weatherTemperatureCelsius ?: return null
    return FragmentWeather(condition = condition, temperatureCelsius = temperature)
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
    locationLabel = location?.label,
    weatherCondition = weather?.condition,
    weatherTemperatureCelsius = weather?.temperatureCelsius
)
