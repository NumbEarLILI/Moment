package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureForPlacePick() {
    webViewClient = WebViewClient()
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
