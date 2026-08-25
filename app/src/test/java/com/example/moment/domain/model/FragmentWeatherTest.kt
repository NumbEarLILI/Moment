package com.example.moment.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentWeatherTest {

    @Test
    fun captionJoinsConditionAndTemperature() {
        assertEquals(
            "晴  26°",
            FragmentWeather(condition = "晴", temperatureCelsius = 26).caption()
        )
    }

    @Test
    fun contextLineWeatherOnly() {
        assertEquals(
            "多云  18°",
            fragmentContextLine(
                weather = FragmentWeather("多云", 18),
                location = null
            )
        )
    }

    @Test
    fun contextLineLocationOnlyUsesPlaceLabel() {
        assertEquals(
            "测试公园",
            fragmentContextLine(
                weather = null,
                location = FragmentLocation(39.9, 116.4, "测试公园")
            )
        )
    }

    @Test
    fun contextLineJoinsWeatherThenPlace() {
        assertEquals(
            "晴  26°  ·  测试公园",
            fragmentContextLine(
                weather = FragmentWeather("晴", 26),
                location = FragmentLocation(39.9, 116.4, "测试公园")
            )
        )
    }

    @Test
    fun contextLineEmptyWhenNeitherPresent() {
        assertNull(fragmentContextLine(weather = null, location = null))
    }

    @Test
    fun ghostPlaceholderIgnoresWeatherOnlyRows() {
        val ghost = LifeFragment(
            stableId = "g",
            content = "",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = java.time.Instant.EPOCH,
            updatedAt = java.time.Instant.EPOCH
        )
        assertTrue(ghost.isNasGhostPlaceholder())
        assertFalse(ghost.copy(weather = FragmentWeather("晴", 26)).isNasGhostPlaceholder())
    }
}
