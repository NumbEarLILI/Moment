package com.example.moment.data.avatar

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UserAvatarStore(
    private val filesDir: File
) {
    fun destination(): File = File(File(filesDir, DIR).apply { mkdirs() }, FILE)

    fun import(input: InputStream): File {
        val dest = destination()
        val tmp = File(dest.parentFile, TMP_FILE)
        try {
            tmp.outputStream().use { output -> input.copyTo(output) }
            try {
                Files.move(
                    tmp.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmp.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            return dest
        } catch (error: Throwable) {
            tmp.delete()
            throw error
        }
    }

    fun clear() {
        val dest = destination()
        dest.delete()
        File(dest.parentFile, TMP_FILE).delete()
    }

    companion object {
        const val DIR = "avatar"
        const val FILE = "profile.jpg"
        const val TMP_FILE = "profile.jpg.tmp"
    }
}
