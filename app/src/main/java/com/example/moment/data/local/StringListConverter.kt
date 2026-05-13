package com.example.moment.data.local

import androidx.room.TypeConverter
import com.example.moment.domain.model.DiaryLocationPin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StringListConverter {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)

    @TypeConverter
    fun fromLongList(value: List<Long>): String = Json.encodeToString(value)

    @TypeConverter
    fun toLongList(value: String): List<Long> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)

    @TypeConverter
    fun fromLocationPins(value: List<DiaryLocationPin>): String = Json.encodeToString(value)

    @TypeConverter
    fun toLocationPins(value: String): List<DiaryLocationPin> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}
