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
    fun cloudyAndPrecipitationLabels() {
        assertEquals("晴间多云", WmoWeatherLabels.chineseCondition(1))
        assertEquals("雾", WmoWeatherLabels.chineseCondition(45))
        assertEquals("毛毛雨", WmoWeatherLabels.chineseCondition(51))
        assertEquals("阵雨", WmoWeatherLabels.chineseCondition(80))
        assertEquals("阵雪", WmoWeatherLabels.chineseCondition(85))
    }
}
