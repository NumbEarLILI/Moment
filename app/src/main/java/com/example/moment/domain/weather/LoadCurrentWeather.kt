package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentLocation

object LoadCurrentWeather {
    suspend fun caption(
        hasPermission: Boolean,
        location: FragmentLocation?,
        fetch: suspend (latitude: Double, longitude: Double) -> CurrentWeather?
    ): String {
        if (!hasPermission) return HomeWeatherCaption.NEED_LOCATION
        if (location == null) return HomeWeatherCaption.UNAVAILABLE
        val weather = fetch(location.latitude, location.longitude)
        return HomeWeatherCaption.from(locationAvailable = true, weather = weather)
    }
}
