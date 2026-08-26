package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadCurrentWeatherTest {

    @Test
    fun missingPermissionAsksToEnablePositioning() = runTest {
        val caption = LoadCurrentWeather.caption(
            hasPermission = false,
            location = null,
            fetch = { _, _ -> CurrentWeather("晴", 26) }
        )
        assertEquals(HomeWeatherCaption.NEED_LOCATION, caption)
    }

    @Test
    fun permissionGrantedButNoFixShowsUnavailable() = runTest {
        val caption = LoadCurrentWeather.caption(
            hasPermission = true,
            location = null,
            fetch = { _, _ -> CurrentWeather("晴", 26) }
        )
        assertEquals(HomeWeatherCaption.UNAVAILABLE, caption)
    }

    @Test
    fun successfulFetchShowsConditionAndTemperature() = runTest {
        val caption = LoadCurrentWeather.caption(
            hasPermission = true,
            location = FragmentLocation(31.23, 121.47, "上海"),
            fetch = { lat, lng ->
                assertEquals(31.23, lat, 0.0)
                assertEquals(121.47, lng, 0.0)
                CurrentWeather("晴", 26)
            }
        )
        assertEquals("晴  26°", caption)
    }
}
