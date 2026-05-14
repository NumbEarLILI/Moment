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
 * 或环境变量 `AMAP_WEB_SERVICE_KEY`（CI Secret）。未配置时 [AmapRegeoResult.failureDetail] 会说明原因。
 */
@Singleton
class AmapReverseGeocoder @Inject constructor() {

    suspend fun reverseGeocode(latitude: Double, longitude: Double): AmapRegeoResult =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.AMAP_WEB_SERVICE_KEY.trim()
            if (key.isEmpty()) {
                return@withContext AmapRegeoResult(null, "未配置 amap.web.service.key（Web 服务 Key）")
            }
            val loc = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
            val built = Uri.parse("https://restapi.amap.com/v3/geocode/regeo").buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("location", loc)
                .appendQueryParameter("extensions", "all")
                .appendQueryParameter("radius", "300")
                .appendQueryParameter("roadlevel", "0")
                .appendQueryParameter("output", "JSON")
                .build()
            val url = URL(built.toString())
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 12_000
                conn.readTimeout = 12_000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext AmapRegeoResult(null, "HTTP $code ${body.take(300)}")
                }
                parseRegeoBody(body)
            } catch (e: Exception) {
                AmapRegeoResult(null, "请求异常: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }

    private fun parseRegeoBody(body: String): AmapRegeoResult {
        return try {
            val root = JSONObject(body)
            if (root.optString("status") != "1") {
                val info = root.optString("info")
                val infocode = root.optString("infocode")
                return AmapRegeoResult(
                    null,
                    "status=${root.optString("status")} info=$info infocode=$infocode"
                )
            }
            val regeo = root.optJSONObject("regeocode")
                ?: return AmapRegeoResult(null, "无 regeocode 节点")
            regeo.optString("formatted_address").trim().takeIf { it.isNotEmpty() }?.let {
                return AmapRegeoResult(it, null)
            }
            val pois = regeo.optJSONArray("pois")
            if (pois != null && pois.length() > 0) {
                val name = pois.getJSONObject(0).optString("name").trim()
                if (name.isNotEmpty()) return AmapRegeoResult(name, null)
            }
            AmapRegeoResult(null, "formatted_address 与 pois 均为空")
        } catch (e: Exception) {
            AmapRegeoResult(null, "JSON 解析失败: ${e.message}")
        }
    }

    private companion object {
        private const val USER_AGENT = "MomentDiary/0.1 (https://github.com/NumbEarLILI/Moment)"
    }
}

data class AmapRegeoResult(
    val label: String?,
    val failureDetail: String?,
)
