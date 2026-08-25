package com.example.moment.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WmoWeatherLabelsTest {

    @Test
    fun clearSkyIsSunny() {
        assertEquals("晴", WmoWeatherLabels.chineseCondition(0))
    }

    @Test
    fun rainCodesShareRainLabel() {
        assertEquals("雨", WmoWeatherLabels.chineseCondition(61))
        assertEquals("雨", WmoWeatherLabels.chineseCondition(65))
    }

    @Test
    fun thunderstormCodesShareStormLabel() {
        assertEquals("雷雨", WmoWeatherLabels.chineseCondition(95))
        assertEquals("雷雨", WmoWeatherLabels.chineseCondition(99))
    }

    @Test
    fun unknownCodeFallsBackToGenericWeather() {
        assertEquals("天气", WmoWeatherLabels.chineseCondition(1234))
    }
}
