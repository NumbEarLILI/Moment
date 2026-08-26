package com.example.moment.data.repository

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
import com.example.moment.domain.model.LifeFragment
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FragmentMapperTest {

    @Test
    fun roundTripPreservesWeatherAndLocation() {
        val fragment = LifeFragment(
            id = 3L,
            stableId = "sid-3",
            content = "出门散步",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = Instant.parse("2026-08-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-25T10:05:00Z"),
            location = FragmentLocation(31.23, 121.47, "上海"),
            weather = FragmentWeather(condition = "晴", temperatureCelsius = 26)
        )

        val roundTripped = fragment.toEntity().toDomain()

        assertEquals(fragment.location, roundTripped.location)
        assertEquals(fragment.weather, roundTripped.weather)
    }

    @Test
    fun missingWeatherColumnsMapToNull() {
        val fragment = LifeFragment(
            id = 1L,
            stableId = "sid-1",
            content = "无天气",
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = Instant.parse("2026-08-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-25T10:00:00Z")
        )

        assertEquals(null, fragment.toEntity().toDomain().weather)
    }
}
