package com.example.moment.data.repository

import com.example.moment.data.local.entity.DiaryEntity
import com.example.moment.domain.model.DiaryEntry
import java.time.Instant
import java.time.LocalDate

fun DiaryEntity.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    body = body,
    highlights = highlights,
    moodSummary = moodSummary,
    sourceFragmentIds = sourceFragmentIds,
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
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)
