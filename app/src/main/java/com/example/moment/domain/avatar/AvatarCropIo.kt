package com.example.moment.domain.avatar

import java.io.InputStream
import java.io.OutputStream

object AvatarCropIo {
    const val MAX_SOURCE_BYTES = 25L * 1024L * 1024L

    fun copyCapped(input: InputStream, output: OutputStream, maxBytes: Long = MAX_SOURCE_BYTES): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) {
                throw IllegalArgumentException("图片太大")
            }
            output.write(buffer, 0, read)
        }
        return copied
    }
}
