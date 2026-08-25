package com.example.moment.data.weather

import com.example.moment.domain.weather.CurrentWeather
import com.example.moment.domain.weather.WmoWeatherLabels
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object OpenMeteoWeatherParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): CurrentWeather? {
        val root = runCatching {
            json.decodeFromString(OpenMeteoForecastDto.serializer(), body)
        }.getOrNull() ?: return null
        val current = root.current ?: return null
        val temperature = current.temperature2m ?: return null
        val code = current.weatherCode ?: return null
        return CurrentWeather(
            condition = WmoWeatherLabels.chineseCondition(code),
            temperatureCelsius = temperature.roundToInt()
        )
    }
}

@Serializable
private data class OpenMeteoForecastDto(
    val current: Current? = null
) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature2m: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null
    )
}
