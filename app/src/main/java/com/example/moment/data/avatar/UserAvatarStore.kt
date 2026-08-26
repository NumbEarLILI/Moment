package com.example.moment.data.avatar

import java.io.File
import java.io.InputStream

class UserAvatarStore(
    private val filesDir: File
) {
    fun destination(): File = File(File(filesDir, DIR).apply { mkdirs() }, FILE)

    fun import(input: InputStream): File {
        val dest = destination()
        dest.outputStream().use { output -> input.copyTo(output) }
        return dest
    }

    fun clear() {
        destination().delete()
    }

    companion object {
        const val DIR = "avatar"
        const val FILE = "profile.jpg"
    }
}
