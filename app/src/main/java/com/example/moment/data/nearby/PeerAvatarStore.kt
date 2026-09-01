package com.example.moment.data.nearby

import java.io.File

class PeerAvatarStore(private val filesDir: File) {
    fun directory(): File = File(filesDir, DIR).apply { mkdirs() }

    fun fileFor(nodeId: String): File = File(directory(), "${sanitize(nodeId)}.jpg")

    fun save(nodeId: String, jpeg: ByteArray): File {
        val dest = fileFor(nodeId)
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.writeBytes(jpeg)
        if (dest.exists()) dest.delete()
        tmp.renameTo(dest)
        return dest
    }

    fun pathIfPresent(nodeId: String): String? =
        fileFor(nodeId).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    fun snapshot(): Map<String, String> =
        directory().listFiles()?.mapNotNull { file ->
            if (!file.isFile || file.extension != "jpg") return@mapNotNull null
            val nodeId = file.nameWithoutExtension
            if (nodeId.isBlank()) null else nodeId to file.absolutePath
        }?.toMap().orEmpty()

    private fun sanitize(nodeId: String): String =
        nodeId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "unknown" }

    private companion object {
        const val DIR = "nearby-avatars"
    }
}
