package com.example.moment.data.weather

import com.example.moment.domain.weather.CurrentWeather
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AmapWeatherParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): CurrentWeather? {
        val root = runCatching {
            json.decodeFromString(AmapWeatherDto.serializer(), body)
        }.getOrNull() ?: return null
        if (root.status != "1") return null
        val live = root.lives.firstOrNull() ?: return null
        val condition = live.weather?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val temperature = live.temperature?.trim()?.toDoubleOrNull()?.roundToInt() ?: return null
        return CurrentWeather(condition = condition, temperatureCelsius = temperature)
    }
}

@Serializable
private data class AmapWeatherDto(
    val status: String? = null,
    val lives: List<Live> = emptyList()
) {
    @Serializable
    data class Live(
        val weather: String? = null,
        val temperature: String? = null
    )
}
