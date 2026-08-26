package com.example.moment.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWeatherCaptionTest {

    @Test
    fun loadingShowsQuietPlaceholder() {
        assertEquals("正在获取天气", HomeWeatherCaption.LOADING)
    }

    @Test
    fun missingLocationAsksToEnablePositioning() {
        assertEquals(
            "定位后显示天气",
            HomeWeatherCaption.from(locationAvailable = false, weather = null)
        )
    }

    @Test
    fun fetchFailureShowsUnavailable() {
        assertEquals(
            "天气暂不可用",
            HomeWeatherCaption.from(locationAvailable = true, weather = null)
        )
    }

    @Test
    fun availableWeatherUsesConditionAndTemperature() {
        assertEquals(
            "晴  26°",
            HomeWeatherCaption.from(
                locationAvailable = true,
                weather = CurrentWeather(condition = "晴", temperatureCelsius = 26)
            )
        )
    }
}
