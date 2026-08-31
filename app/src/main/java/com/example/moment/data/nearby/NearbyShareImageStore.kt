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

    fun deleteIfManaged(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        val file = File(trimmed)
        val dir = runCatching { directory().canonicalFile }.getOrNull() ?: return
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (canonical == dir || !canonical.path.startsWith(dir.path + File.separator)) return
        if (canonical.isFile) canonical.delete()
    }

    private fun sanitize(messageId: String): String =
        messageId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "share" }

    private companion object {
        const val DIR = "nearby-fragment-shares"
    }
}
