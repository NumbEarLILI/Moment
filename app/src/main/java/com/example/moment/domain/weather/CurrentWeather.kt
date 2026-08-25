package com.example.moment.domain.weather

data class CurrentWeather(
    val condition: String,
    val temperatureCelsius: Int
) {
    fun headerCaption(): String = "$condition  ${temperatureCelsius}°"
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
