package com.example.moment.data.weather

import android.net.Uri
import com.example.moment.domain.weather.CurrentWeather
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Singleton
class OpenMeteoWeatherClient @Inject constructor() {

    suspend fun fetchCurrent(latitude: Double, longitude: Double): CurrentWeather? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse("https://api.open-meteo.com/v1/forecast").buildUpon()
                .appendQueryParameter("latitude", String.format(Locale.US, "%.4f", latitude))
                .appendQueryParameter("longitude", String.format(Locale.US, "%.4f", longitude))
                .appendQueryParameter("current", "temperature_2m,weather_code")
                .appendQueryParameter("timezone", "auto")
                .build()
            val conn = URL(uri.toString()).openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 12_000
                conn.readTimeout = 12_000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                OpenMeteoWeatherParser.parse(body)
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    private companion object {
        private const val USER_AGENT = "MomentDiary/0.1 (https://github.com/NumbEarLILI/Moment)"
    }
}
