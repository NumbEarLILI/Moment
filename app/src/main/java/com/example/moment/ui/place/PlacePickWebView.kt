package com.example.moment.ui.place

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

internal fun placePickerAssetUrl(latitude: Double, longitude: Double): String {
    val lat = Uri.encode(String.format(Locale.US, "%.7f", latitude))
    val lng = Uri.encode(String.format(Locale.US, "%.7f", longitude))
    return "file:///android_asset/place_picker.html?lat=$lat&lng=$lng"
}

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureForPlacePick() {
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
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
