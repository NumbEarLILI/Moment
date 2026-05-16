package com.example.moment.data.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalImageJpegBase64Encoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 将图片压缩为 JPEG 再 Base64，控制体积以适配多模态 API。
     */
    fun encodeJpegBase64(uriString: String, maxEdgePx: Int = 1280, jpegQuality: Int = 82): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream) ?: return@use null
                val toEncode = scaleDownIfNeeded(decoded, maxEdgePx)
                if (toEncode !== decoded) {
                    decoded.recycle()
                }
                ByteArrayOutputStream().use { baos ->
                    toEncode.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    toEncode.recycle()
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }
            }
        }.getOrNull()
    }

    private fun scaleDownIfNeeded(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
