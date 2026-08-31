package com.example.moment.data.nearby

/** 保存到相册时用的类型嗅探；不依赖 Android 框架，方便单测。 */
object NearbyShareImageMime {
    fun fromBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == JPEG_SOI_0 &&
            bytes[1] == JPEG_SOI_1 &&
            bytes[2] == JPEG_SOI_2
        ) {
            return MIME_JPEG
        }
        if (bytes.size >= 8 &&
            bytes[0] == PNG_0 &&
            bytes[1] == PNG_1 &&
            bytes[2] == PNG_2 &&
            bytes[3] == PNG_3
        ) {
            return MIME_PNG
        }
        return MIME_JPEG
    }

    fun fileExtension(mime: String): String = when (mime) {
        MIME_PNG -> "png"
        else -> "jpg"
    }

    fun displayName(nowMillis: Long, mime: String): String =
        "Moment_${nowMillis}.${fileExtension(mime)}"

    fun needsLegacyWritePermission(sdkInt: Int): Boolean = sdkInt < 29

    private const val MIME_JPEG = "image/jpeg"
    private const val MIME_PNG = "image/png"
    private val JPEG_SOI_0 = 0xFF.toByte()
    private val JPEG_SOI_1 = 0xD8.toByte()
    private val JPEG_SOI_2 = 0xFF.toByte()
    private val PNG_0 = 0x89.toByte()
    private val PNG_1 = 0x50.toByte()
    private val PNG_2 = 0x4E.toByte()
    private val PNG_3 = 0x47.toByte()
}
