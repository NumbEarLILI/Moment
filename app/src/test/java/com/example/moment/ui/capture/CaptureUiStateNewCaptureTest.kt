package com.example.moment.ui.capture

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureUiStateNewCaptureTest {
    @Test
    fun forNewCaptureClearsDraftAndKeepsHomeWeather() {
        val previous = CaptureUiState(
            content = "刚记下的碎片",
            tags = "散步",
            imageUris = "content://photo",
            saved = true,
            isSaving = true,
            errorMessage = "旧错误",
            locationOverride = null,
            weatherCaption = "晴  26°",
            nasArchiveSyncMessage = "已同步",
            composeWeather = FragmentWeather("阴", 18)
        )
        val recordedAt = Instant.parse("2026-08-26T01:30:00Z")
        val next = previous.forNewCapture(
            recordedDate = "2026-08-26",
            recordedTime = "09:30",
            recordedAt = recordedAt,
            composeWeather = FragmentWeather("晴", 26)
        )

        assertEquals("", next.content)
        assertEquals("", next.tags)
        assertEquals("", next.imageUris)
        assertFalse(next.saved)
        assertFalse(next.isSaving)
        assertNull(next.errorMessage)
        assertEquals(0L, next.editingFragmentId)
        assertEquals("晴  26°", next.weatherCaption)
        assertEquals("已同步", next.nasArchiveSyncMessage)
        assertEquals(FragmentWeather("晴", 26), next.composeWeather)
        assertEquals("2026-08-26", next.recordedDate)
        assertEquals("09:30", next.recordedTime)
        assertEquals(recordedAt, next.baselineRecordedAt)
        assertNull(next.locationOverride)
        assertNull(next.baselineLocation)
        assertFalse(next.isResolvingPlace)
    }

    @Test
    fun forNewCaptureClearsPreviouslyPickedPlace() {
        val previous = CaptureUiState(
            locationOverride = FragmentLocation(30.0, 120.0, "西湖"),
            baselineLocation = FragmentLocation(31.0, 121.0, "外滩"),
            isResolvingPlace = true
        )
        val next = previous.forNewCapture(
            recordedDate = "2026-08-26",
            recordedTime = "09:30",
            recordedAt = Instant.parse("2026-08-26T01:30:00Z"),
            composeWeather = null
        )
        assertNull(next.locationOverride)
        assertNull(next.baselineLocation)
        assertFalse(next.isResolvingPlace)
    }
}
