package com.example.moment.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FragmentWeather(
    val condition: String,
    val temperatureCelsius: Int
) {
    fun caption(): String = "$condition  ${temperatureCelsius}°"
}

fun fragmentContextLine(
    weather: FragmentWeather?,
    location: FragmentLocation?
): String? {
    val weatherText = weather?.caption()
    val place = location?.let { loc ->
        loc.label?.trim()?.takeIf { it.isNotEmpty() }
            ?: String.format(java.util.Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
    }
    return listOfNotNull(weatherText, place).joinToString("  ·  ").takeIf { it.isNotEmpty() }
}
