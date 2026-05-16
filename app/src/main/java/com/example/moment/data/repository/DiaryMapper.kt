package com.example.moment.data.repository

import com.example.moment.data.local.entity.DiaryEntity
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentAiStory
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val fragmentStoriesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val fragmentStoriesListSerializer = ListSerializer(FragmentAiStory.serializer())

fun decodeFragmentStories(json: String): List<FragmentAiStory> =
    if (json.isBlank()) emptyList() else fragmentStoriesJson.decodeFromString(fragmentStoriesListSerializer, json)

fun encodeFragmentStories(items: List<FragmentAiStory>): String =
    if (items.isEmpty()) "[]" else fragmentStoriesJson.encodeToString(fragmentStoriesListSerializer, items)

fun DiaryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    body = body,
    highlights = highlights,
    moodSummary = moodSummary,
    sourceFragmentIds = sourceFragmentIds,
    imageUris = imageUris,
    locationPins = locationPins,
    fragmentStories = decodeFragmentStories(fragmentStoriesJson),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)

fun DiaryEntry.toEntity(): DiaryEntity = DiaryEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    title = title,
    body = body,
    highlights = highlights,
    moodSummary = moodSummary,
    sourceFragmentIds = sourceFragmentIds,
    imageUris = imageUris,
    locationPins = locationPins,
    fragmentStoriesJson = encodeFragmentStories(fragmentStories),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)
