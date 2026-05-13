package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.moment.data.location.PlacePickMapHtml
import com.example.moment.domain.model.FragmentLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlacePickerJsBridge(
    private val onPick: (Double, Double) -> Unit
) {
    @JavascriptInterface
    fun onPick(latitude: Double, longitude: Double) {
        Handler(Looper.getMainLooper()).post {
            onPick(latitude, longitude)
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
    val html = remember(state.mapLat, state.mapLng) {
        PlacePickMapHtml.build(state.mapLat, state.mapLng)
    }

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
                "拖动图钉或点击地图选点；可编辑下方名称。若地图无法加载，仍可手动填写名称并保存（使用当前坐标）。",
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
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(
                            PlacePickerJsBridge { lat, lng -> viewModel.onMapPosition(lat, lng) },
                            "AndroidHost"
                        )
                        webViewRef = this
                        loadDataWithBaseURL(
                            "https://localhost/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        "https://localhost/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            )
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
