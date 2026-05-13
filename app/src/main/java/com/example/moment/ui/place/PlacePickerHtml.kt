package com.example.moment.ui.place

import android.net.Uri
import java.util.Locale

/**
 * Embeds 高德地图 JS API 2.0（国内网络友好）。需在 `local.properties` 中配置：
 * `amap.web.key=你的Web端(JS API)Key`（控制台 https://console.amap.com/ 免费申请）。
 * CI 可通过环境变量 `AMAP_WEB_KEY` 注入；未配置时显示说明页，仍可用初始坐标保存。
 */
object PlacePickerHtml {

    /** 与 [android.webkit.WebView.loadDataWithBaseURL] 的 baseUrl 一致，便于高德侧校验。 */
    const val LOAD_BASE_URL: String = "https://lbs.amap.com/"

    fun build(latitude: Double, longitude: Double, amapWebJsKey: String): String {
        val latStr = String.format(Locale.US, "%.7f", latitude)
        val lngStr = String.format(Locale.US, "%.7f", longitude)
        val key = amapWebJsKey.trim()
        if (key.isEmpty()) {
            return noKeyHtml(latStr, lngStr)
        }
        val scriptSrc = "https://webapi.amap.com/maps?v=2.0&key=" + Uri.encode(key, "UTF-8")
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>html,body,#map{height:100%;margin:0;padding:0;background:#f5f5f5;}</style>
<script>
function showErr(msg) {
  var el = document.getElementById('map');
  if (el) {
    el.innerHTML = '<div style="padding:16px;color:#555;font-size:14px;line-height:1.6;">' + msg + '</div>';
  }
}
function initMap() {
  if (typeof AMap === 'undefined') {
    showErr('高德地图脚本未加载，请检查网络或 Key 是否有效。');
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
<div id="map"></div>
<script>
(function() {
  var el = document.getElementById('amap-sdk');
  if (el) {
    el.onerror = function() {
      showErr('无法从高德服务器加载地图脚本，请检查网络或 Key 配置。');
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
请在项目根目录的 <b>local.properties</b> 中添加一行：<br/>
<code style="background:#eee;padding:2px 6px;border-radius:4px;">amap.web.key=你的Web端(JS API) Key</code><br/><br/>
Key 在 <b>高德开放平台</b>（console.amap.com）免费申请，应用类型请选择支持 <b>JS API 2.0</b> 的 Web 端；若 Key 启用了安全限制，请将请求来源与当前页面域（lbs.amap.com）加入白名单。
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
