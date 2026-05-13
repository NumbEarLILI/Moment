package com.example.moment.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewFragmentRecordedAtTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun returnsNullWhenNoAnchorDateSoCallerUsesPlainNow() {
        val clock = Clock.fixed(Instant.parse("2026-05-13T08:15:30Z"), zoneId)
        assertNull(resolveNewFragmentRecordedAt(clock, zoneId, null))
    }

    @Test
    fun returnsNullWhenAnchorIsTodaySoCallerUsesPlainNow() {
        val clock = Clock.fixed(Instant.parse("2026-05-13T08:15:30Z"), zoneId)
        val today = LocalDate.of(2026, 5, 13)
        assertNull(resolveNewFragmentRecordedAt(clock, zoneId, today))
    }

    @Test
    fun combinesPastAnchorDateWithCurrentLocalTimeOfDay() {
        val clock = Clock.fixed(Instant.parse("2026-05-13T08:15:30Z"), zoneId)
        val yesterday = LocalDate.of(2026, 5, 12)
        val expected = yesterday.atTime(16, 15, 30).atZone(zoneId).toInstant()
        assertEquals(expected, resolveNewFragmentRecordedAt(clock, zoneId, yesterday))
    }

    @Test
    fun combinesFutureAnchorDateWithCurrentLocalTimeOfDay() {
        val clock = Clock.fixed(Instant.parse("2026-05-13T08:15:30Z"), zoneId)
        val tomorrow = LocalDate.of(2026, 5, 14)
        val expected = tomorrow.atTime(16, 15, 30).atZone(zoneId).toInstant()
        assertEquals(expected, resolveNewFragmentRecordedAt(clock, zoneId, tomorrow))
    }
}
