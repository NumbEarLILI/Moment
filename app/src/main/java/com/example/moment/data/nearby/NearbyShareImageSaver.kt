package com.example.moment.data.nearby

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** 把聊天里点开的碎片图存进系统相册 Pictures/Moment。 */
object NearbyShareImageSaver {
    fun save(context: Context, sourcePath: String): Result<Uri> = runCatching {
        val bytes = readSource(context, sourcePath) ?: error("找不到这张图片")
        if (bytes.isEmpty()) error("找不到这张图片")
        val mime = NearbyShareImageMime.fromBytes(bytes)
        val name = NearbyShareImageMime.displayName(System.currentTimeMillis(), mime)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Moment"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法写入相册")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入相册")
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun readSource(context: Context, sourcePath: String): ByteArray? {
        val trimmed = sourcePath.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("://")) {
            return context.contentResolver.openInputStream(Uri.parse(trimmed))?.use { it.readBytes() }
        }
        val file = File(trimmed)
        return if (file.isFile) file.readBytes() else null
    }
}
