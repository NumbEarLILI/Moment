package com.example.moment.data.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Singleton
class NominatimReverseGeocoder @Inject constructor() {

    suspend fun reverseLabel(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&accept-language=zh"
        )
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("display_name").takeIf { it.isNotBlank() }
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
