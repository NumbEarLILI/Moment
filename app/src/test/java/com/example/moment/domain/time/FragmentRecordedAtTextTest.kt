package com.example.moment.domain.time

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentRecordedAtTextTest {

    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun formatFragmentRecordedAtText_usesLocalDateAndMinute() {
        val text = formatFragmentRecordedAtText(
            Instant.parse("2026-05-13T14:30:45Z"),
            zoneId
        )

        assertEquals("2026-05-13", text.date)
        assertEquals("22:30", text.time)
    }

    @Test
    fun parseFragmentRecordedAtText_combinesDateAndTimeInZone() {
        val instant = parseFragmentRecordedAtText(
            dateText = "2026-05-13",
            timeText = "22:30",
            zoneId = zoneId
        )

        assertEquals(Instant.parse("2026-05-13T14:30:00Z"), instant)
    }

    @Test
    fun parseFragmentRecordedAtText_returnsNullForInvalidText() {
        assertNull(parseFragmentRecordedAtText("2026/05/13", "22:30", zoneId))
        assertNull(parseFragmentRecordedAtText("2026-05-13", "25:30", zoneId))
    }

    @Test
    fun resolveFragmentRecordedAtForSave_preservesBaselinePrecisionWhenTextUnchanged() {
        val baseline = Instant.parse("2026-05-13T14:30:45.123Z")
        val text = FragmentRecordedAtText(date = "2026-05-13", time = "22:30")
        val parsed = Instant.parse("2026-05-13T14:30:00Z")

        val resolved = resolveFragmentRecordedAtForSave(
            parsedRecordedAt = parsed,
            currentText = text,
            baselineRecordedAt = baseline,
            baselineText = text
        )

        assertEquals(baseline, resolved)
    }

    @Test
    fun resolveFragmentRecordedAtForSave_usesParsedTimeWhenTextChanged() {
        val baseline = Instant.parse("2026-05-13T14:30:45.123Z")
        val parsed = Instant.parse("2026-05-13T15:00:00Z")

        val resolved = resolveFragmentRecordedAtForSave(
            parsedRecordedAt = parsed,
            currentText = FragmentRecordedAtText(date = "2026-05-13", time = "23:00"),
            baselineRecordedAt = baseline,
            baselineText = FragmentRecordedAtText(date = "2026-05-13", time = "22:30")
        )

        assertEquals(parsed, resolved)
    }
}
