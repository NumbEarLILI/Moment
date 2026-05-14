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
 * 使用单独的 **Web 服务** Key（[BuildConfig.AMAP_WEB_SERVICE_KEY]）。
 * 若控制台为 Key 启用了「数字签名」，请在 `local.properties` 配置 `amap.web.service.secret`（与 Key 配套的签名密钥）。
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
            val params = linkedMapOf(
                "extensions" to "all",
                "key" to key,
                "location" to loc,
                "output" to "JSON",
                "radius" to "300",
                "roadlevel" to "0"
            )
            val secret = BuildConfig.AMAP_WEB_SERVICE_SECRET.trim()
            if (secret.isNotEmpty()) {
                params["sig"] = amapWebServiceSig(params, secret)
            }
            val uri = Uri.parse("https://restapi.amap.com/v3/geocode/regeo").buildUpon()
            params.forEach { (k, v) -> uri.appendQueryParameter(k, v) }
            val url = URL(uri.build().toString())
            val conn = url.openConnection() as HttpsURLConnection
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
                if (code !in 200..299) {
                    return@withContext AmapRegeoResult(null, "HTTP $code ${body.take(400)}")
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
            if (body.isBlank()) {
                return AmapRegeoResult(null, "响应体为空")
            }
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
            humanFromAddressComponent(regeo.optJSONObject("addressComponent"))?.let {
                return AmapRegeoResult(it, null)
            }
            val pois = regeo.optJSONArray("pois")
            if (pois != null && pois.length() > 0) {
                val name = pois.getJSONObject(0).optString("name").trim()
                if (name.isNotEmpty()) return AmapRegeoResult(name, null)
            }
            AmapRegeoResult(null, "formatted_address、addressComponent、pois 均无可用文本")
        } catch (e: Exception) {
            AmapRegeoResult(null, "JSON 解析失败: ${e.message} body=${body.take(200)}")
        }
    }

    private fun humanFromAddressComponent(ac: JSONObject?): String? {
        if (ac == null) return null
        val sb = StringBuilder()
        fun appendString(key: String) {
            val v = ac.optString(key).trim()
            if (v.isNotEmpty() && v != "[]") sb.append(v)
        }
        appendString("province")
        when (val cityAny = ac.opt("city")) {
            is String -> {
                val c = cityAny.trim()
                if (c.isNotEmpty() && c != "[]") sb.append(c)
            }
        }
        appendString("district")
        appendString("township")
        val sn = ac.optJSONObject("streetNumber")
        if (sn != null) {
            val st = sn.optString("street").trim()
            val num = sn.optString("number").trim()
            if (st.isNotEmpty()) sb.append(st)
            if (num.isNotEmpty()) sb.append(num)
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        private const val USER_AGENT = "MomentDiary/0.1 (https://github.com/NumbEarLILI/Moment)"

        /** 高德 Web 服务 MD5 签名，见 https://lbs.amap.com/api/webservice/guide/create-project/signature */
        private fun amapWebServiceSig(params: Map<String, String>, secret: String): String {
            val sorted = params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
            val raw = sorted + secret
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { b -> "%02x".format(b) }.uppercase(Locale.US)
        }
    }
}

data class AmapRegeoResult(
    val label: String?,
    val failureDetail: String?,
)
