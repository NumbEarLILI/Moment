package com.example.moment.data.nearby

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.moment.domain.nearby.NearbyAvatarPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** 把本机头像或碎片图压成一张很小的 JPEG，好在蓝牙链路上发给邻居。 */
object NearbyAvatarThumbnail {
    fun fromFile(file: File): ByteArray? {
        if (!file.isFile) return null
        return runCatching {
            val original = decodeFile(file) ?: return@runCatching null
            encode(original)
        }.getOrNull()
    }

    fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray? {
        return runCatching {
            val original = decodeUri(resolver, uri) ?: return@runCatching null
            encode(original)
        }.getOrNull()
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

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = NearbyAvatarPolicy.decodeSampleSize(bounds.outWidth, bounds.outHeight)
            }
        ) ?: return null
        return orient(decoded, readExif(file))
    }

    private fun decodeUri(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = NearbyAvatarPolicy.decodeSampleSize(bounds.outWidth, bounds.outHeight)
                }
            )
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use { readExif(it) }
            ?: ExifInterface.ORIENTATION_NORMAL
        return orient(decoded, orientation)
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

    private fun readExif(file: File): Int =
        try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun readExif(input: InputStream): Int =
        try {
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
