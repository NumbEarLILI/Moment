package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherCaptionParserTest {

    @Test
    fun parsesConditionAndTemperature() {
        assertEquals(
            FragmentWeather("晴", 26),
            parseWeatherCaption("晴  26°")
        )
    }

    @Test
    fun rejectsPlaceholders() {
        assertNull(parseWeatherCaption(HomeWeatherCaption.LOADING))
        assertNull(parseWeatherCaption(HomeWeatherCaption.NEED_LOCATION))
        assertNull(parseWeatherCaption(HomeWeatherCaption.UNAVAILABLE))
    }
}
