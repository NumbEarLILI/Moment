package com.example.moment.data.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.example.moment.domain.avatar.AvatarCropPixelRect
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

class AvatarCropProcessor(
    private val filesDir: File
) {
    fun previewFile(): File = File(File(filesDir, UserAvatarStore.DIR).apply { mkdirs() }, PREVIEW_FILE)

    fun preparePreview(openStream: () -> InputStream?): AvatarCropPreview {
        val dest = previewFile()
        val raw = File(dest.parentFile, RAW_FILE)
        try {
            val stream = openStream() ?: error("无法读取所选图片")
            stream.use { input -> raw.outputStream().use { output -> input.copyTo(output) } }
            if (raw.length() == 0L) error("无法读取所选图片")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("无法读取所选图片")

            val orientation = readExifOrientation(raw)
            val decoded = BitmapFactory.decodeFile(
                raw.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_SOURCE_EDGE_PX)
                }
            ) ?: error("无法读取所选图片")
            val oriented = applyExifOrientation(decoded, orientation)
            var toWrite = oriented
            try {
                val largestEdge = max(oriented.width, oriented.height)
                if (largestEdge > MAX_SOURCE_EDGE_PX) {
                    val scale = MAX_SOURCE_EDGE_PX.toFloat() / largestEdge.toFloat()
                    toWrite = Bitmap.createScaledBitmap(
                        oriented,
                        (oriented.width * scale).roundToInt().coerceAtLeast(1),
                        (oriented.height * scale).roundToInt().coerceAtLeast(1),
                        true
                    )
                }
                dest.outputStream().use { output ->
                    if (!toWrite.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output)) {
                        error("无法读取所选图片")
                    }
                }
                return AvatarCropPreview(
                    file = dest,
                    width = toWrite.width,
                    height = toWrite.height
                )
            } finally {
                if (toWrite !== oriented) toWrite.recycle()
                if (oriented !== decoded) oriented.recycle()
                decoded.recycle()
            }
        } catch (error: Throwable) {
            dest.delete()
            throw error
        } finally {
            raw.delete()
        }
    }

    fun cropToJpeg(preview: File, rect: AvatarCropPixelRect): ByteArray {
        val source = BitmapFactory.decodeFile(preview.absolutePath) ?: error("无法读取所选图片")
        try {
            val square = Bitmap.createBitmap(source, rect.x, rect.y, rect.size, rect.size)
            val output = if (square.width == OUTPUT_SIZE_PX && square.height == OUTPUT_SIZE_PX) {
                square
            } else {
                Bitmap.createScaledBitmap(square, OUTPUT_SIZE_PX, OUTPUT_SIZE_PX, true)
            }
            try {
                val bytes = ByteArrayOutputStream()
                if (!output.compress(Bitmap.CompressFormat.JPEG, OUTPUT_JPEG_QUALITY, bytes)) {
                    error("无法设置头像")
                }
                return bytes.toByteArray()
            } finally {
                if (output !== square) output.recycle()
                if (square !== source) square.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    fun clearPreview() {
        previewFile().delete()
        File(previewFile().parentFile, RAW_FILE).delete()
    }

    private fun readExifOrientation(file: File): Int =
        try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
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
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (max(halfWidth / sampleSize, halfHeight / sampleSize) >= maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        const val PREVIEW_FILE = "crop_preview.jpg"
        const val RAW_FILE = "crop_raw.tmp"
        const val MAX_SOURCE_EDGE_PX = 2048
        const val OUTPUT_SIZE_PX = 512
        const val PREVIEW_JPEG_QUALITY = 90
        const val OUTPUT_JPEG_QUALITY = 90
    }
}

data class AvatarCropPreview(
    val file: File,
    val width: Int,
    val height: Int
)
