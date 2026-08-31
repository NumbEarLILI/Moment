package com.example.moment.data.nearby

import java.io.File

/** 邻居分享过来的碎片图，按 messageId 落盘。 */
class NearbyShareImageStore(private val filesDir: File) {
    fun directory(): File = File(filesDir, DIR).apply { mkdirs() }

    fun fileFor(messageId: String): File = File(directory(), "${sanitize(messageId)}.jpg")

    fun save(messageId: String, jpeg: ByteArray): File {
        val dest = fileFor(messageId)
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.writeBytes(jpeg)
        if (dest.exists()) dest.delete()
        tmp.renameTo(dest)
        return dest
    }

    private fun sanitize(messageId: String): String =
        messageId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "share" }

    private companion object {
        const val DIR = "nearby-fragment-shares"
    }
}
