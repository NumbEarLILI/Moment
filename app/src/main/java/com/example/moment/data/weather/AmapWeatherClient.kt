package com.example.moment.data.weather

import android.net.Uri
import com.example.moment.BuildConfig
import com.example.moment.data.location.AmapReverseGeocoder
import com.example.moment.domain.weather.CurrentWeather
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AmapWeatherClient @Inject constructor(
    private val reverseGeocoder: AmapReverseGeocoder
) {
    suspend fun fetchCurrent(latitude: Double, longitude: Double): CurrentWeather? {
        val adcode = reverseGeocoder.reverseGeocode(latitude, longitude).adcode ?: return null
        return fetchByAdcode(adcode)
    }

    private suspend fun fetchByAdcode(adcode: String): CurrentWeather? = withContext(Dispatchers.IO) {
        val key = BuildConfig.AMAP_WEB_SERVICE_KEY.trim()
        if (key.isEmpty()) return@withContext null
        val params = linkedMapOf(
            "city" to adcode,
            "extensions" to "base",
            "key" to key,
            "output" to "JSON"
        )
        val secret = BuildConfig.AMAP_WEB_SERVICE_SECRET.trim()
        if (secret.isNotEmpty()) {
            params["sig"] = amapWebServiceSig(params, secret)
        }
        val uri = Uri.parse("https://restapi.amap.com/v3/weather/weatherInfo").buildUpon()
        params.forEach { (k, v) -> uri.appendQueryParameter(k, v) }
        val conn = URL(uri.build().toString()).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://lbs.amap.com/")
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return@withContext null
            AmapWeatherParser.parse(body)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        private const val USER_AGENT = "MomentDiary/0.1 (https://github.com/NumbEarLILI/Moment)"

        private fun amapWebServiceSig(params: Map<String, String>, secret: String): String {
            val sorted = params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
            val raw = sorted + secret
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { b -> "%02x".format(b) }.uppercase(Locale.US)
        }
    }
}
