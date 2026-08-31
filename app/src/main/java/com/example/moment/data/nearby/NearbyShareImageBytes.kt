package com.example.moment.data.nearby

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.moment.domain.nearby.NearbyAvatarPolicy
import com.example.moment.domain.nearby.NearbyFragmentSharePolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** 读取碎片原图。小于上限时原样发送；更大则按照片质量压缩，不是头像缩略图。 */
object NearbyShareImageBytes {
    fun fromAny(path: String, resolver: ContentResolver): ByteArray {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return byteArrayOf()
        val raw = readRaw(trimmed, resolver) ?: return byteArrayOf()
        return fit(raw)
    }

    fun fromFile(file: File): ByteArray {
        if (!file.isFile) return byteArrayOf()
        if (file.length() <= NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_BYTES) {
            return runCatching { file.readBytes() }.getOrDefault(byteArrayOf())
        }
        return recompressFile(file) ?: byteArrayOf()
    }

    fun fit(
        raw: ByteArray,
        maxBytes: Int = NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_BYTES
    ): ByteArray {
        if (raw.isEmpty() || raw.size <= maxBytes) return raw
        return recompressBytes(raw, maxBytes) ?: byteArrayOf()
    }

    private fun readRaw(path: String, resolver: ContentResolver): ByteArray? {
        if (path.startsWith("content:", ignoreCase = true)) {
            return resolver.openInputStream(Uri.parse(path))?.use { readCapped(it) }
        }
        if (path.startsWith("file:", ignoreCase = true)) {
            val uri = Uri.parse(path)
            val file = uri.path?.let(::File)
            if (file != null && file.isFile) return fromFile(file)
            return resolver.openInputStream(uri)?.use { readCapped(it) }
        }
        val file = File(path)
        if (file.isFile) return fromFile(file)
        return runCatching {
            resolver.openInputStream(Uri.parse(path))?.use { readCapped(it) }
        }.getOrNull()
    }

    private fun readCapped(input: InputStream, maxBytes: Int = MAX_SOURCE_BYTES): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            total += read
            if (total > maxBytes) break
            out.write(buf, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun recompressFile(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = NearbyAvatarPolicy.decodeSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_EDGE_PX
                )
            }
        ) ?: return null
        val oriented = orient(decoded, readExifFromFile(file))
        return encode(oriented)
    }

    private fun recompressBytes(raw: ByteArray, maxBytes: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val decoded = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply {
                inSampleSize = NearbyAvatarPolicy.decodeSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_EDGE_PX
                )
            }
        ) ?: return null
        val oriented = orient(decoded, readExif(raw))
        return encode(oriented, maxBytes)
    }

    private fun encode(
        original: Bitmap,
        maxBytes: Int = NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_BYTES
    ): ByteArray? {
        return try {
            val scaled = scale(original)
            val bytes = compress(scaled, maxBytes)
            if (scaled !== original) scaled.recycle()
            bytes
        } finally {
            original.recycle()
        }
    }

    private fun scale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        val maxEdge = NearbyFragmentSharePolicy.WIFI_MAX_IMAGE_EDGE_PX
        if (longest <= maxEdge) return source
        val factor = maxEdge.toFloat() / longest
        val width = (source.width * factor).toInt().coerceAtLeast(1)
        val height = (source.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun compress(bitmap: Bitmap, maxBytes: Int): ByteArray? {
        var quality = NearbyFragmentSharePolicy.WIFI_JPEG_QUALITY
        var last: ByteArray? = null
        while (quality >= 40) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            last = bytes
            if (bytes.size <= maxBytes) return bytes
            quality -= 12
        }
        return last?.takeIf { it.size <= maxBytes }
    }

    private fun readExifFromFile(file: File): Int =
        try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun readExif(bytes: ByteArray): Int =
        try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
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

private const val MAX_SOURCE_BYTES = 16 * 1024 * 1024
