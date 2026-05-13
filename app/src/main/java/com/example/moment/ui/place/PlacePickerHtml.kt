package com.example.moment.ui.place

import android.net.Uri
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 高德地图 JS API 2.0（国内）。
 *
 * - **Key**：`local.properties` → `amap.web.key=`，或环境变量 `AMAP_WEB_KEY`（GitHub Actions Secret）。
 * - **安全密钥**（2021-12-02 之后申请的 Key 必填）：`amap.security.jscode=`，或 `AMAP_SECURITY_JS_CODE` Secret。
 *   在高德控制台「应用」→「Key 设置」旁可查看「安全密钥」说明与复制入口。
 */
object PlacePickerHtml {

    const val LOAD_BASE_URL: String = "https://lbs.amap.com/"

    fun build(
        latitude: Double,
        longitude: Double,
        amapWebJsKey: String,
        amapSecurityJsCode: String
    ): String {
        val latStr = String.format(Locale.US, "%.7f", latitude)
        val lngStr = String.format(Locale.US, "%.7f", longitude)
        val key = amapWebJsKey.trim()
        if (key.isEmpty()) {
            return noKeyHtml(latStr, lngStr)
        }
        val security = amapSecurityJsCode.trim()
        val securitySetup = if (security.isNotEmpty()) {
            val secJson = Json.encodeToString(JsonPrimitive(security))
            "window._AMapSecurityConfig = { securityJsCode: $secJson };\n"
        } else {
            ""
        }
        val warnBanner = if (security.isEmpty()) {
            """
<div style="background:#fff3cd;color:#664d03;padding:8px 10px;font-size:13px;line-height:1.45;border-bottom:1px solid #ffe69c;">
提示：2021年12月2日后申请的 Key 必须配置<strong>安全密钥</strong>地图才会显示。请在 <code>local.properties</code> 增加
<code style="background:#fffde7;padding:1px 4px;border-radius:3px;">amap.security.jscode=你的安全密钥</code>（与 Key 在同一应用下获取），然后重新编译安装。
</div>
            """.trimIndent()
        } else {
            ""
        }
        val scriptSrc = "https://webapi.amap.com/maps?v=2.0&key=" + Uri.encode(key, "UTF-8")
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>html,body{height:100%;margin:0;padding:0;background:#f5f5f5;}body{display:flex;flex-direction:column;}#warn{flex-shrink:0;}#map{flex:1;min-height:0;position:relative;}</style>
<script>
$securitySetup
function showErr(msg) {
  var el = document.getElementById('map');
  if (el) {
    el.innerHTML = '<div style="padding:16px;color:#555;font-size:14px;line-height:1.6;flex:1;">' + msg + '</div>';
  }
}
function initMap() {
  if (typeof AMap === 'undefined') {
    showErr('高德地图脚本未加载，请检查网络、Key 与安全密钥是否正确。');
    return;
  }
  var lat = $latStr, lng = $lngStr;
  var map = new AMap.Map('map', {
    resizeEnable: true,
    zoom: 15,
    center: [lng, lat]
  });
  var marker = new AMap.Marker({
    position: [lng, lat],
    map: map,
    draggable: true
  });
  map.on('click', function(ev) {
    marker.setPosition(ev.lnglat);
  });
  window.sendPick = function() {
    var p = marker.getPosition();
    AndroidHost.onPick(p.lat, p.lng);
  };
}
</script>
<script id="amap-sdk" src="$scriptSrc" onload="initMap()"></script>
</head>
<body>
<div id="warn">$warnBanner</div>
<div id="map"></div>
<script>
(function() {
  var el = document.getElementById('amap-sdk');
  if (el) {
    el.onerror = function() {
      showErr('无法从高德服务器加载地图脚本，请检查网络、Key 或白名单设置。');
    };
  }
})();
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun noKeyHtml(latStr: String, lngStr: String): String =
        """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>html,body,#map{height:100%;margin:0;padding:0;} body{font-family:sans-serif;background:#fafafa;}</style>
</head>
<body>
<div id="map" style="display:flex;flex-direction:column;justify-content:center;padding:16px;box-sizing:border-box;">
<p style="margin:0 0 10px 0;color:#333;font-size:15px;font-weight:600;">未配置高德地图 Key</p>
<p style="margin:0;color:#555;font-size:14px;line-height:1.65;">
请在项目根目录的 <b>local.properties</b> 中添加：<br/>
<code style="background:#eee;padding:2px 6px;border-radius:4px;">amap.web.key=你的Web端(JS API) Key</code><br/><br/>
<strong>安全密钥</strong>（2021-12-02 后申请的 Key 必填）：<br/>
<code style="background:#eee;padding:2px 6px;border-radius:4px;">amap.security.jscode=你的安全密钥</code><br/><br/>
均可在 <b>高德开放平台</b>（console.amap.com）对应应用中获取。
</p>
<p style="margin:12px 0 0;color:#888;font-size:13px;">未加载地图时，「读取图钉位置」仍使用当前坐标：纬度 $latStr，经度 $lngStr。</p>
</div>
<script>
function sendPick() {
  AndroidHost.onPick(parseFloat('$latStr'), parseFloat('$lngStr'));
}
</script>
</body>
</html>
        """.trimIndent()
}
