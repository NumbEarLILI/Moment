package com.example.moment.ui.place

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 高德地图 JS API 2.0（国内）。
 *
 * 使用官方 [loader.js + AMapLoader.load](https://developer.amap.com/api/javascript-api-v2/getting-started)，
 * 比直接引用 `maps?v=2.0` 在 WebView 中更稳定。
 *
 * - **Key**：`amap.web.key` / `AMAP_WEB_KEY`（须为控制台 **Web 端（JS API）** 类型，不是纯 Android SDK Key）。
 * - **安全密钥**：`amap.security.jscode` / `AMAP_SECURITY_JS_CODE`（2021-12-02 后申请的 Key 必填）。
 * - **白名单**：若 Key 启用了「请求来源 / 域名」限制，请将页面基址域名加入白名单（与 [LOAD_BASE_URL] 一致，例如 `https://lbs.amap.com/` 并允许子路径）。
 * - **坐标**：传入的初始经纬度应为 **GCJ-02**（与高德国测加密一致）。保存碎片时自动定位已做 WGS→GCJ 转换。
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
        val keyJson = Json.encodeToString(JsonPrimitive(key))
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
        val loaderSrc = "https://webapi.amap.com/loader.js"
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>html,body{height:100%;margin:0;padding:0;overflow:hidden;background:#e8e8e8;}#warn{position:absolute;top:0;left:0;right:0;z-index:10;pointer-events:none;}#warn *{pointer-events:auto;}#map{position:absolute;top:0;left:0;right:0;bottom:0;width:100%;height:100%;min-height:100vh;}</style>
<script>
$securitySetup
(function() {
  if (window.AndroidHost && AndroidHost.onMapTrace) {
    AndroidHost.onMapTrace('picker page script start');
  }
})();
function showErr(msg) {
  var el = document.getElementById('map');
  if (el) {
    el.innerHTML = '<div style="padding:16px;color:#555;font-size:14px;line-height:1.6;flex:1;">' + msg + '</div>';
  }
}
var lat = $latStr, lng = $lngStr;
function initWithAMap(AMap) {
  try {
    if (window.AndroidHost && AndroidHost.onMapTrace) {
      AndroidHost.onMapTrace('initWithAMap: creating map');
    }
    var map = new AMap.Map('map', {
      resizeEnable: true,
      zoom: 15,
      center: [lng, lat],
      viewMode: '2D',
      features: ['bg', 'point', 'road', 'building']
    });
    if (window.AndroidHost && AndroidHost.onMapTrace) {
      AndroidHost.onMapTrace('Map instance created');
    }
    map.on('complete', function() {
      if (window.AndroidHost && AndroidHost.onMapTrace) {
        AndroidHost.onMapTrace('map event: complete');
      }
      setTimeout(function() {
        try { map.resize(); } catch (e1) {}
      }, 50);
    });
    function momentLngLatToStrings(pos) {
      if (!pos) return null;
      if (Array.isArray(pos) && pos.length >= 2) {
        var lngA = Number(pos[0]);
        var latA = Number(pos[1]);
        if (latA === latA && lngA === lngA) return ['' + latA, '' + lngA];
      }
      var lat = (typeof pos.getLat === 'function') ? pos.getLat() : pos.lat;
      var lng = (typeof pos.getLng === 'function') ? pos.getLng() : pos.lng;
      if (lat == null || lng == null) return null;
      lat = Number(lat);
      lng = Number(lng);
      if (lat !== lat || lng !== lng) return null;
      return ['' + lat, '' + lng];
    }
    function notifyPickFromMarker(m) {
      try {
        var pair = momentLngLatToStrings(m.getPosition());
        if (!pair) {
          if (window.AndroidHost && AndroidHost.onMapError) {
            AndroidHost.onMapError('notifyPickFromMarker: 无法解析图钉坐标');
          }
          return;
        }
        if (window.AndroidHost && AndroidHost.onPick) {
          AndroidHost.onPick(pair[0], pair[1]);
        }
      } catch (err) {
        var msg = (err && err.message) ? err.message : String(err);
        if (window.AndroidHost && AndroidHost.onMapError) {
          AndroidHost.onMapError('notifyPickFromMarker: ' + msg);
        }
      }
    }
    var marker = new AMap.Marker({
      position: [lng, lat],
      map: map,
      draggable: true
    });
    marker.on('dragend', function() {
      notifyPickFromMarker(marker);
    });
    map.on('click', function(ev) {
      marker.setPosition(ev.lnglat);
      notifyPickFromMarker(marker);
    });
    window.sendPick = function() {
      notifyPickFromMarker(marker);
    };
    setTimeout(function() { notifyPickFromMarker(marker); }, 200);
    window.__momentResizeMap = function() {
      try { map.resize(); } catch (e2) {}
    };
    setTimeout(function() { window.__momentResizeMap && window.__momentResizeMap(); }, 80);
    setTimeout(function() { window.__momentResizeMap && window.__momentResizeMap(); }, 400);
    setTimeout(function() { window.__momentResizeMap && window.__momentResizeMap(); }, 1200);
    setTimeout(function() {
      var canvas = document.querySelector('#map canvas');
      if (!canvas) {
        if (window.AndroidHost && AndroidHost.onMapError) {
          AndroidHost.onMapError('3.5s check: no canvas in #map (WebView height 0 or tiles blocked)');
        }
      } else {
        var w = canvas.width, h = canvas.height;
        if (window.AndroidHost && AndroidHost.onMapTrace) {
          AndroidHost.onMapTrace('3.5s check: canvas ' + w + 'x' + h);
        }
        if ((!w || !h) && window.AndroidHost && AndroidHost.onMapError) {
          AndroidHost.onMapError('canvas size is 0');
        }
      }
    }, 3500);
  } catch (e) {
    var m = (e && e.message) ? e.message : String(e);
    showErr('地图初始化失败：' + m);
    if (window.AndroidHost && AndroidHost.onMapError) {
      AndroidHost.onMapError(String(m));
    }
  }
}
function bootLoader() {
  if (window.AndroidHost && AndroidHost.onMapTrace) {
    AndroidHost.onMapTrace('loader.js onload -> bootLoader');
  }
  if (typeof AMapLoader === 'undefined') {
    var msg = '未加载高德 loader（请检查网络或 webapi.amap.com 是否被拦截）';
    showErr(msg);
    if (window.AndroidHost && AndroidHost.onMapError) {
      AndroidHost.onMapError('AMapLoader undefined');
    }
    return;
  }
  AMapLoader.load({
    key: $keyJson,
    version: '2.0'
  }).then(initWithAMap).catch(function(e) {
    var m = (e && (e.message || e.msg || e.info)) ? (e.message || e.msg || e.info) : String(e);
    showErr('地图模块加载失败：' + m);
    if (window.AndroidHost && AndroidHost.onMapError) {
      AndroidHost.onMapError(String(m));
    }
  });
}
</script>
<script id="amap-loader" src="$loaderSrc" onload="bootLoader()" onerror="showErr('无法加载 loader.js，请检查网络或白名单（需允许 webapi.amap.com）。'); if(window.AndroidHost &amp;&amp; AndroidHost.onMapError){AndroidHost.onMapError('loader script onerror');}"></script>
</head>
<body>
<div id="warn">$warnBanner</div>
<div id="map"></div>
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
Key 类型须为 <b>Web端（JS API）</b>；若启用域名白名单，请加入 <code>https://lbs.amap.com/*</code>。
</p>
<p style="margin:12px 0 0;color:#888;font-size:13px;">未加载地图时，「读取图钉位置」仍使用当前坐标：纬度 $latStr，经度 $lngStr。</p>
</div>
<script>
window.sendPick = function() {
  if (window.AndroidHost && AndroidHost.onPick) {
    AndroidHost.onPick('$latStr', '$lngStr');
  }
};
</script>
</body>
</html>
        """.trimIndent()
}
