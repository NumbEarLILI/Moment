package com.example.moment.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoWeatherParserTest {

    @Test
    fun parsesCurrentTemperatureAndSunnyCondition() {
        val weather = OpenMeteoWeatherParser.parse(
            """
            {
              "latitude": 31.23,
              "longitude": 121.47,
              "current": {
                "time": "2026-08-25T15:00",
                "interval": 900,
                "temperature_2m": 26.4,
                "weather_code": 0
              }
            }
            """.trimIndent()
        )

        assertEquals("晴", weather?.condition)
        assertEquals(26, weather?.temperatureCelsius)
        assertEquals("晴  26°", weather?.headerCaption())
    }

    @Test
    fun roundsHalfUpForDisplayTemperature() {
        val weather = OpenMeteoWeatherParser.parse(
            """{"current":{"temperature_2m":26.5,"weather_code":2}}"""
        )

        assertEquals("多云", weather?.condition)
        assertEquals(27, weather?.temperatureCelsius)
        assertEquals("多云  27°", weather?.headerCaption())
    }

    @Test
    fun missingCurrentBlockReturnsNull() {
        assertNull(OpenMeteoWeatherParser.parse("""{"latitude":31.23}"""))
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(OpenMeteoWeatherParser.parse("not-json"))
    }
}
