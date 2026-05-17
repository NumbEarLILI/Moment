package com.example.moment.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class FragmentRecordedAtText(
    val date: String,
    val time: String
)

private val FragmentRecordedDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val FragmentRecordedTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatFragmentRecordedAtText(instant: Instant, zoneId: ZoneId): FragmentRecordedAtText {
    val zoned = instant.atZone(zoneId)
    return FragmentRecordedAtText(
        date = zoned.toLocalDate().format(FragmentRecordedDateFormatter),
        time = zoned.toLocalTime().format(FragmentRecordedTimeFormatter)
    )
}

fun parseFragmentRecordedAtText(
    dateText: String,
    timeText: String,
    zoneId: ZoneId
): Instant? =
    try {
        val date = LocalDate.parse(dateText.trim(), FragmentRecordedDateFormatter)
        val time = LocalTime.parse(timeText.trim(), FragmentRecordedTimeFormatter)
        date.atTime(time).atZone(zoneId).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
