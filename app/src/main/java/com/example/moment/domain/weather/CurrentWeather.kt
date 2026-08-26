package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentWeather

data class CurrentWeather(
    val condition: String,
    val temperatureCelsius: Int
) {
    fun headerCaption(): String = "$condition  ${temperatureCelsius}°"

    fun toFragmentWeather(): FragmentWeather =
        FragmentWeather(condition = condition, temperatureCelsius = temperatureCelsius)
}

object HomeWeatherCaption {
    const val LOADING = "正在获取天气"
    const val NEED_LOCATION = "定位后显示天气"
    const val UNAVAILABLE = "天气暂不可用"

    fun from(locationAvailable: Boolean, weather: CurrentWeather?): String {
        if (!locationAvailable) return NEED_LOCATION
        return weather?.headerCaption() ?: UNAVAILABLE
    }
}

private val WeatherCaptionRegex = Regex("""^(.+?) {2}(-?\d+)°$""")

fun parseWeatherCaption(caption: String): FragmentWeather? {
    val trimmed = caption.trim()
    if (
        trimmed == HomeWeatherCaption.LOADING ||
        trimmed == HomeWeatherCaption.NEED_LOCATION ||
        trimmed == HomeWeatherCaption.UNAVAILABLE
    ) {
        return null
    }
    val match = WeatherCaptionRegex.matchEntire(trimmed) ?: return null
    val condition = match.groupValues[1].trim()
    val temperature = match.groupValues[2].toIntOrNull() ?: return null
    if (condition.isEmpty()) return null
    return FragmentWeather(condition = condition, temperatureCelsius = temperature)
}
