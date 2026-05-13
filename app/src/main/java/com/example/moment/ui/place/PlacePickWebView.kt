package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureForPlacePick(onLoadingDiagnostic: ((String) -> Unit)? = null) {
    webViewClient = object : WebViewClient() {
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (request.isForMainFrame) {
                    val msg = "${error.description} (code=${error.errorCode}) url=${request.url}"
                    onLoadingDiagnostic?.invoke("WebView: $msg")
                }
            }
            super.onReceivedError(view, request, error)
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                onLoadingDiagnostic?.invoke("WebView: $description (code=$errorCode) url=$failingUrl")
            }
            @Suppress("DEPRECATION")
            super.onReceivedError(view, errorCode, description, failingUrl)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            val code = errorResponse.statusCode
            if (code >= 400) {
                val url = request.url.toString()
                val important = request.isForMainFrame ||
                    url.contains("loader.js", ignoreCase = true) ||
                    url.contains("/maps?", ignoreCase = true)
                if (important) {
                    onLoadingDiagnostic?.invoke("HTTP $code $url")
                }
            }
            super.onReceivedHttpError(view, request, errorResponse)
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
                Log.d(
                    "MomentPlacePickJs",
                    "${it.messageLevel()} ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                )
            }
            return super.onConsoleMessage(consoleMessage)
        }
    }
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true
        val defaultUa = userAgentString
        userAgentString = "Moment/0.1 (+https://github.com/NumbEarLILI/Moment) $defaultUa"
    }
}

internal fun WebView.loadPlacePickerHtml(html: String) {
    loadDataWithBaseURL(
        PlacePickerHtml.LOAD_BASE_URL,
        html,
        "text/html",
        "utf-8",
        null
    )
}

internal fun WebView.requestPlaceMapResize() {
    evaluateJavascript(
        "try{if(window.__momentResizeMap){window.__momentResizeMap();}}catch(e){}",
        null
    )
}
