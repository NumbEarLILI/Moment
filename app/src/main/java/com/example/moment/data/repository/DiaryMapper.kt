package com.example.moment.data.repository

import com.example.moment.data.local.entity.DiaryEntity
import com.example.moment.domain.model.DiaryEntry
import com.example.moment.domain.model.FragmentAiStory
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val fragmentStoriesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val fragmentStoriesListSerializer = ListSerializer(FragmentAiStory.serializer())

private val fragmentImageUrisByIdJsonSerializer =
    MapSerializer(String.serializer(), ListSerializer(String.serializer()))

fun decodeFragmentStories(json: String): List<FragmentAiStory> =
    if (json.isBlank()) emptyList() else fragmentStoriesJson.decodeFromString(fragmentStoriesListSerializer, json)

fun encodeFragmentStories(items: List<FragmentAiStory>): String =
    if (items.isEmpty()) "[]" else fragmentStoriesJson.encodeToString(fragmentStoriesListSerializer, items)

fun decodeFragmentImageUris(json: String): Map<Long, List<String>> {
    if (json.isBlank() || json == "{}") return emptyMap()
    val raw = fragmentStoriesJson.decodeFromString(fragmentImageUrisByIdJsonSerializer, json)
    return raw.mapNotNull { (key, uris) ->
        val id = key.toLongOrNull() ?: return@mapNotNull null
        val cleaned = uris.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) null else id to cleaned
    }.toMap()
}

fun encodeFragmentImageUris(map: Map<Long, List<String>>): String {
    if (map.isEmpty()) return "{}"
    val stringMap = map
        .mapValues { (_, uris) -> uris.map { it.trim() }.filter { it.isNotEmpty() } }
        .filterValues { it.isNotEmpty() }
        .mapKeys { it.key.toString() }
    if (stringMap.isEmpty()) return "{}"
    return fragmentStoriesJson.encodeToString(fragmentImageUrisByIdJsonSerializer, stringMap)
}

fun DiaryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    body = body,
    highlights = highlights,
    moodSummary = moodSummary,
    sourceFragmentIds = sourceFragmentIds,
    imageUris = imageUris,
    fragmentImageUris = decodeFragmentImageUris(fragmentImageUrisJson),
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
    fragmentImageUrisJson = encodeFragmentImageUris(fragmentImageUris),
    locationPins = locationPins,
    fragmentStoriesJson = encodeFragmentStories(fragmentStories),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)
