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

private val fragmentImageUrisJsonSerializer =
    MapSerializer(String.serializer(), ListSerializer(String.serializer()))

private val fragmentCreatedAtJsonSerializer =
    MapSerializer(String.serializer(), Long.serializer())

fun decodeFragmentStories(json: String): List<FragmentAiStory> =
    if (json.isBlank()) emptyList() else fragmentStoriesJson.decodeFromString(fragmentStoriesListSerializer, json)

fun encodeFragmentStories(items: List<FragmentAiStory>): String =
    if (items.isEmpty()) "[]" else fragmentStoriesJson.encodeToString(fragmentStoriesListSerializer, items)

fun decodeFragmentImageUris(json: String): Map<String, List<String>> {
    if (json.isBlank() || json == "{}") return emptyMap()
    val raw = fragmentStoriesJson.decodeFromString(fragmentImageUrisJsonSerializer, json)
    return raw
        .mapValues { (_, uris) -> uris.map { it.trim() }.filter { it.isNotEmpty() } }
        .filterValues { it.isNotEmpty() }
}

fun encodeFragmentImageUris(map: Map<String, List<String>>): String {
    if (map.isEmpty()) return "{}"
    val cleaned = map
        .mapValues { (_, uris) -> uris.map { it.trim() }.filter { it.isNotEmpty() } }
        .filterValues { it.isNotEmpty() }
    if (cleaned.isEmpty()) return "{}"
    return fragmentStoriesJson.encodeToString(fragmentImageUrisJsonSerializer, cleaned)
}

fun decodeFragmentCreatedAtEpochMillis(json: String): Map<String, Long> {
    if (json.isBlank() || json == "{}") return emptyMap()
    val raw = fragmentStoriesJson.decodeFromString(fragmentCreatedAtJsonSerializer, json)
    return cleanFragmentCreatedAtEpochMillis(raw)
}

fun encodeFragmentCreatedAtEpochMillis(map: Map<String, Long>): String {
    val cleaned = cleanFragmentCreatedAtEpochMillis(map)
    if (cleaned.isEmpty()) return "{}"
    return fragmentStoriesJson.encodeToString(fragmentCreatedAtJsonSerializer, cleaned)
}

private fun cleanFragmentCreatedAtEpochMillis(map: Map<String, Long>): Map<String, Long> =
    map.mapNotNull { (k, v) ->
        val key = k.trim()
        if (key.isEmpty()) null else key to v
    }.toMap()

fun DiaryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    body = body,
    highlights = highlights,
    moodSummary = moodSummary,
    sourceFragmentStableIds = sourceFragmentStableIds,
    imageUris = imageUris,
    fragmentImageUris = decodeFragmentImageUris(fragmentImageUrisJson),
    locationPins = locationPins,
    fragmentStories = decodeFragmentStories(fragmentStoriesJson),
    fragmentCreatedAtEpochMillis = decodeFragmentCreatedAtEpochMillis(fragmentCreatedAtEpochMillisJson),
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
    sourceFragmentStableIds = sourceFragmentStableIds,
    imageUris = imageUris,
    fragmentImageUrisJson = encodeFragmentImageUris(fragmentImageUris),
    locationPins = locationPins,
    fragmentStoriesJson = encodeFragmentStories(fragmentStories),
    fragmentCreatedAtEpochMillisJson = encodeFragmentCreatedAtEpochMillis(fragmentCreatedAtEpochMillis),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)
