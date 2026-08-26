package com.example.moment.data.weather

import com.example.moment.data.location.ChinaCoordinateTransform
import com.example.moment.domain.weather.CurrentWeather
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeWeatherRepository @Inject constructor(
    private val amapWeather: AmapWeatherClient,
    private val openMeteo: OpenMeteoWeatherClient
) {
    suspend fun fetchCurrent(latitude: Double, longitude: Double): CurrentWeather? {
        runCatching { amapWeather.fetchCurrent(latitude, longitude) }.getOrNull()?.let { return it }
        val (wgsLat, wgsLng) = ChinaCoordinateTransform.gcj02ToWgs84(latitude, longitude)
        return runCatching { openMeteo.fetchCurrent(wgsLat, wgsLng) }.getOrNull()
    }
}
