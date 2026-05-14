package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.moment.BuildConfig
import com.example.moment.domain.model.FragmentLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlacePickerJsBridge(
    private val onPickFromWeb: (Double, Double) -> Unit,
    private val onMapError: (String) -> Unit,
    private val onMapTrace: (String) -> Unit
) {
    /**
     * 必须用字符串传经纬度：部分 WebView 下从 `evaluateJavascript` 触发的 JS 用 `Number` 调
     * `onPick(double,double)` 会匹配失败且静默无回调；字符串与高德 LngLat 的 getter 都更稳。
     */
    @JavascriptInterface
    fun onPick(latitude: String, longitude: String) {
        Handler(Looper.getMainLooper()).post {
            val lat = latitude.trim().toDoubleOrNull()
            val lng = longitude.trim().toDoubleOrNull()
            if (lat != null && lng != null) {
                onPickFromWeb(lat, lng)
            } else {
                onMapError("onPick 无法解析: lat=\"$latitude\" lng=\"$longitude\"")
            }
        }
    }

    @JavascriptInterface
    fun onMapError(message: String) {
        Handler(Looper.getMainLooper()).post {
            onMapError(message)
        }
    }

    @JavascriptInterface
    fun onMapTrace(message: String) {
        Handler(Looper.getMainLooper()).post {
            onMapTrace(message)
        }
    }
}

const val MOMENT_PICK_LOCATION_JSON_KEY = "moment_pick_location_json"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlacePickScreen(
    navController: NavHostController,
    onClose: () -> Unit,
    viewModel: PlacePickViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val pickerHtml = remember(
        state.mapLat,
        state.mapLng,
        BuildConfig.AMAP_WEB_JS_KEY,
        BuildConfig.AMAP_SECURITY_JS_CODE
    ) {
        PlacePickerHtml.build(
            state.mapLat,
            state.mapLng,
            BuildConfig.AMAP_WEB_JS_KEY,
            BuildConfig.AMAP_SECURITY_JS_CODE
        )
    }
    var lastLoadedHtml by remember { mutableStateOf<String?>(null) }
    val traceScroll = rememberScrollState()

    LaunchedEffect(state.finishedLocation) {
        val done = state.finishedLocation ?: return@LaunchedEffect
        val json = Json.encodeToString(FragmentLocation.serializer(), done)
        navController.previousBackStackEntry?.savedStateHandle?.set(MOMENT_PICK_LOCATION_JSON_KEY, json)
        viewModel.consumeFinish()
        navController.popBackStack()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择地点名称", style = MaterialTheme.typography.titleMedium)
            Text(
                "① amap.web.key + amap.security.jscode（地图）；② amap.web.service.key（逆地理）；若控制台为 Web 服务 Key 启用了数字签名，再加 amap.web.service.secret。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.placeName,
                onValueChange = viewModel::updatePlaceName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地点名称") },
                minLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        viewModel.reportMapTrace("【读取图钉】已点击")
                        if (webViewRef == null) {
                            viewModel.reportMapTrace("【读取图钉】WebView 未就绪，请等地图加载后再试")
                            return@TextButton
                        }
                        // 不要用旧 WebView.post：Compose 重组后实例可能已换，旧队列上的 Runnable 不会执行。
                        Handler(Looper.getMainLooper()).post {
                            viewModel.reportMapTrace("【读取图钉】主线程任务开始")
                            val live = webViewRef
                            if (live == null) {
                                viewModel.reportMapTrace("【读取图钉】执行时 WebView 已为空")
                                return@post
                            }
                            val js =
                                "(function(){try{" +
                                    "if(window.AndroidHost&&AndroidHost.onMapTrace)" +
                                    "{AndroidHost.onMapTrace('【读取图钉】JS开始');}" +
                                    "if(typeof window.sendPick!=='function'){" +
                                    "if(window.AndroidHost&&AndroidHost.onMapTrace)" +
                                    "{AndroidHost.onMapTrace('sendPick 未定义');}" +
                                    "return 'no_sendPick';}" +
                                    "window.sendPick();" +
                                    "return 'ok';" +
                                    "}catch(e){" +
                                    "var m=(e&&e.message)?e.message:String(e);" +
                                    "if(window.AndroidHost&&AndroidHost.onMapError)" +
                                    "{AndroidHost.onMapError('sendPick:'+m);}" +
                                    "return 'err';" +
                                    "}})();"
                            live.evaluateJavascript(js) { value ->
                                Handler(Looper.getMainLooper()).post {
                                    viewModel.reportMapTrace("【读取图钉】evaluateJavascript 回调: ${value ?: "null"}")
                                }
                            }
                        }
                    }
                ) {
                    Text("读取图钉位置")
                }
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 280.dp)
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        configureForPlacePick(viewModel::reportMapDiagnostic)
                        addJavascriptInterface(
                            PlacePickerJsBridge(
                                onPickFromWeb = { lat, lng -> viewModel.onMapPosition(lat, lng) },
                                onMapError = viewModel::reportMapDiagnostic,
                                onMapTrace = viewModel::reportMapTrace
                            ),
                            "AndroidHost"
                        )
                        webViewRef = this
                        loadPlacePickerHtml(pickerHtml)
                        lastLoadedHtml = pickerHtml
                        post { requestPlaceMapResize() }
                        postDelayed({ requestPlaceMapResize() }, 400L)
                        postDelayed({ requestPlaceMapResize() }, 1200L)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    if (pickerHtml != lastLoadedHtml) {
                        lastLoadedHtml = pickerHtml
                        webView.loadPlacePickerHtml(pickerHtml)
                        webView.post { webView.requestPlaceMapResize() }
                        webView.postDelayed({ webView.requestPlaceMapResize() }, 400L)
                        webView.postDelayed({ webView.requestPlaceMapResize() }, 1200L)
                    }
                }
            )
            Text(
                "当前图钉：" + String.format(Locale.CHINA, "%.6f，%.6f", state.mapLat, state.mapLng),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("运行与逆地理诊断", style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (state.mapTrace.isBlank()) {
                    "若进入本页后仍只有本行，请更新安装版本。正常会先出现「【配置】…」再出现「【逆地理】…」。"
                } else {
                    state.mapTrace
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 168.dp)
                    .verticalScroll(traceScroll),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.mapDiagnostics.isNotBlank()) {
                Text(
                    state.mapDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isSaving) "保存中…" else "保存地点")
                }
            }
        }
    }
}
