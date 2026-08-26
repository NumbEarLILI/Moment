package com.example.moment.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmapWeatherParserTest {

    @Test
    fun parsesLiveWeatherAndTemperature() {
        val weather = AmapWeatherParser.parse(
            """
            {
              "status": "1",
              "lives": [
                {
                  "city": "黄浦区",
                  "weather": "多云",
                  "temperature": "26"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("多云", weather?.condition)
        assertEquals(26, weather?.temperatureCelsius)
        assertEquals("多云  26°", weather?.headerCaption())
    }

    @Test
    fun failedStatusReturnsNull() {
        assertNull(
            AmapWeatherParser.parse("""{"status":"0","info":"INVALID_USER_KEY","lives":[]}""")
        )
    }

    @Test
    fun emptyLivesReturnsNull() {
        assertNull(AmapWeatherParser.parse("""{"status":"1","lives":[]}"""))
    }
}
