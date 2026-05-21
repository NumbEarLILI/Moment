package com.example.moment.ui.diary

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val PlogTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val PlogTimePattern = Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")

internal fun plogTimeText(instant: Instant, zoneId: ZoneId): String =
    instant.atZone(zoneId).toLocalTime().format(PlogTimeFormatter)

internal fun plogTimeTextsForFragments(
    fragments: List<com.example.moment.domain.model.LifeFragment>,
    zoneId: ZoneId
): Map<String, String> =
    fragments.associate { it.stableId to plogTimeText(it.createdAt, zoneId) }

internal fun updatePlogTimeText(
    state: DiaryEditorUiState,
    stableId: String,
    timeText: String,
    zoneId: ZoneId
): DiaryEditorUiState {
    val key = stableId.trim()
    if (key.isEmpty()) return state
    val nextTexts = state.plogTimeTexts + (key to timeText)
    val parsed = parsePlogTimeOnDiaryDate(state.date, timeText, zoneId)
        ?: return state.copy(
            plogTimeTexts = nextTexts,
            errorMessage = "时间格式应为 HH:mm，例如 09:30"
        )
    val epochMillis = parsed.toEpochMilli()
    return state.copy(
        plogTimeTexts = nextTexts,
        fragmentCreatedAtEpochMillis = state.fragmentCreatedAtEpochMillis + (key to epochMillis),
        plogFragments = state.plogFragments.map {
            if (it.stableId == key) it.copy(createdAt = parsed) else it
        },
        errorMessage = null
    )
}

internal fun invalidPlogTimeMessage(state: DiaryEditorUiState, zoneId: ZoneId): String? {
    for (stableId in state.sourceFragmentStableIds) {
        val key = stableId.trim()
        if (key.isEmpty()) continue
        val text = state.plogTimeTexts[key] ?: continue
        if (parsePlogTimeOnDiaryDate(state.date, text, zoneId) == null) {
            return "请先修正 plog 时间：$text"
        }
    }
    return null
}

private fun parsePlogTimeOnDiaryDate(date: LocalDate, timeText: String, zoneId: ZoneId): Instant? {
    return try {
        val trimmed = timeText.trim()
        if (!PlogTimePattern.matches(trimmed)) return null
        val time = LocalTime.parse(trimmed, PlogTimeFormatter)
        date.atTime(time).atZone(zoneId).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}
