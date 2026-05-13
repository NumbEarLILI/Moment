package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
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
    private val onPick: (Double, Double) -> Unit,
    private val onMapError: (String) -> Unit,
    private val onMapTrace: (String) -> Unit
) {
    @JavascriptInterface
    fun onPick(latitude: Double, longitude: Double) {
        Handler(Looper.getMainLooper()).post {
            onPick(latitude, longitude)
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
                "底图使用高德地图（官方 loader 加载）。Key 须为「Web端（JS API）」并启用 **Web服务**（逆地理）以便自动读出地名；域名白名单需包含 https://lbs.amap.com/ 与 restapi.amap.com。local.properties：amap.web.key、amap.security.jscode。",
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
                        webViewRef?.evaluateJavascript("javascript:sendPick();", null)
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
                                onPick = { lat, lng -> viewModel.onMapPosition(lat, lng) },
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
                    if (pickerHtml != lastLoadedHtml) {
                        lastLoadedHtml = pickerHtml
                        webView.loadPlacePickerHtml(pickerHtml)
                        webView.post { webView.requestPlaceMapResize() }
                        webView.postDelayed({ webView.requestPlaceMapResize() }, 400L)
                        webView.postDelayed({ webView.requestPlaceMapResize() }, 1200L)
                    }
                }
            )
            if (state.mapTrace.isNotBlank()) {
                Text(
                    state.mapTrace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 88.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
