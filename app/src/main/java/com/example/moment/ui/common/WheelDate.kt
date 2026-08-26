package com.example.moment.ui.common

import java.time.LocalDate
import java.time.YearMonth

internal fun clampedLocalDate(year: Int, month: Int, dayOfMonth: Int): LocalDate {
    val safeMonth = month.coerceIn(1, 12)
    val length = YearMonth.of(year, safeMonth).lengthOfMonth()
    return LocalDate.of(year, safeMonth, dayOfMonth.coerceIn(1, length))
}

internal fun parseIsoDateOrNull(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()
