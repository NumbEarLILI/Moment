package com.example.moment.data.location

import android.net.Uri
import com.example.moment.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 高德逆地理编码（国内可用）。
 *
 * 使用单独的 **Web 服务** Key（[BuildConfig.AMAP_WEB_SERVICE_KEY]），与 JS 地图用的
 * [BuildConfig.AMAP_WEB_JS_KEY] 分开配置：`local.properties` 里 `amap.web.service.key=`，
 * 或环境变量 `AMAP_WEB_SERVICE_KEY`（CI Secret）。未配置时跳过高德逆地理。
 */
@Singleton
class AmapReverseGeocoder @Inject constructor() {

    suspend fun reverseLabel(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.AMAP_WEB_SERVICE_KEY.trim()
        if (key.isEmpty()) return@withContext null
        val loc = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        val built = Uri.parse("https://restapi.amap.com/v3/geocode/regeo").buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("location", loc)
            .appendQueryParameter("extensions", "all")
            .appendQueryParameter("radius", "300")
            .appendQueryParameter("roadlevel", "0")
            .build()
        val url = URL(built.toString())
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseRegeo(body)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRegeo(body: String): String? {
        val root = JSONObject(body)
        if (root.optString("status") != "1") return null
        val regeo = root.optJSONObject("regeocode") ?: return null
        regeo.optString("formatted_address").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val pois = regeo.optJSONArray("pois") ?: return null
        if (pois.length() == 0) return null
        return pois.getJSONObject(0).optString("name").trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        private const val USER_AGENT = "MomentDiary/0.1 (https://github.com/NumbEarLILI/Moment)"
    }
}
