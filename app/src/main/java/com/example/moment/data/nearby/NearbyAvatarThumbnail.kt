package com.example.moment.data.nearby

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.moment.domain.nearby.NearbyAvatarPolicy
import java.io.ByteArrayOutputStream
import java.io.File

/** 把本机头像压成一张很小的 JPEG，好在蓝牙链路上发给邻居。 */
object NearbyAvatarThumbnail {
    fun fromFile(file: File): ByteArray? {
        if (!file.isFile) return null
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return encode(original)
    }

    fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray? {
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        return encode(original)
    }

    fun fromAny(path: String, resolver: ContentResolver): ByteArray? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("content:", ignoreCase = true)) {
            return fromUri(resolver, Uri.parse(trimmed))
        }
        val file = if (trimmed.startsWith("file:", ignoreCase = true)) {
            File(Uri.parse(trimmed).path ?: return fromUri(resolver, Uri.parse(trimmed)))
        } else {
            File(trimmed)
        }
        return fromFile(file) ?: runCatching { fromUri(resolver, Uri.parse(trimmed)) }.getOrNull()
    }

    private fun encode(original: Bitmap): ByteArray? {
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
