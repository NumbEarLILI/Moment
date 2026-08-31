package com.example.moment.data.nearby

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.moment.domain.nearby.NearbyAvatarPolicy
import java.io.ByteArrayOutputStream
import java.io.File

/** 把本机头像压成一张很小的 JPEG，好在蓝牙链路上发给邻居。 */
object NearbyAvatarThumbnail {
    fun fromFile(file: File): ByteArray? {
        if (!file.isFile) return null
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            val scaled = scale(original)
            val bytes = compress(scaled)
            if (scaled !== original) scaled.recycle()
            bytes?.takeIf { NearbyAvatarPolicy.acceptable(it.size) }
        } finally {
            original.recycle()
        }
    }

    private fun scale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        if (longest <= NearbyAvatarPolicy.MAX_EDGE_PX) return source
        val scale = NearbyAvatarPolicy.MAX_EDGE_PX.toFloat() / longest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun compress(bitmap: Bitmap): ByteArray? {
        var quality = NearbyAvatarPolicy.JPEG_QUALITY
        while (quality >= 30) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= NearbyAvatarPolicy.MAX_BYTES) return bytes
            quality -= 10
        }
        return null
    }
}
