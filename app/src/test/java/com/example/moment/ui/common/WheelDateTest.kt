package com.example.moment.ui.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WheelDateTest {
    @Test
    fun clampsFebruaryInNonLeapYear() {
        assertEquals(
            LocalDate.of(2025, 2, 28),
            clampedLocalDate(2025, 2, 31)
        )
    }

    @Test
    fun keepsLeapDay() {
        assertEquals(
            LocalDate.of(2024, 2, 29),
            clampedLocalDate(2024, 2, 31)
        )
    }

    @Test
    fun parseIsoDateOrNullAcceptsIsoText() {
        assertEquals(LocalDate.of(2026, 5, 13), parseIsoDateOrNull("2026-05-13"))
        assertNull(parseIsoDateOrNull("2026/05/13"))
        assertNull(parseIsoDateOrNull(""))
    }
}
