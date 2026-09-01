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
        if (bytes.size >= 12 &&
            bytes[0] == RIFF_0 &&
            bytes[1] == RIFF_1 &&
            bytes[2] == RIFF_2 &&
            bytes[3] == RIFF_3 &&
            bytes[8] == WEBP_0 &&
            bytes[9] == WEBP_1 &&
            bytes[10] == WEBP_2 &&
            bytes[11] == WEBP_3
        ) {
            return MIME_WEBP
        }
        return MIME_JPEG
    }

    fun fileExtension(mime: String): String = when (mime) {
        MIME_PNG -> "png"
        MIME_WEBP -> "webp"
        else -> "jpg"
    }

    fun displayName(nowMillis: Long, mime: String): String =
        "Moment_${nowMillis}.${fileExtension(mime)}"

    fun needsLegacyWritePermission(sdkInt: Int): Boolean = sdkInt < 29

    private const val MIME_JPEG = "image/jpeg"
    private const val MIME_PNG = "image/png"
    private const val MIME_WEBP = "image/webp"
    private val JPEG_SOI_0 = 0xFF.toByte()
    private val JPEG_SOI_1 = 0xD8.toByte()
    private val JPEG_SOI_2 = 0xFF.toByte()
    private val PNG_0 = 0x89.toByte()
    private val PNG_1 = 0x50.toByte()
    private val PNG_2 = 0x4E.toByte()
    private val PNG_3 = 0x47.toByte()
    private val RIFF_0 = 0x52.toByte()
    private val RIFF_1 = 0x49.toByte()
    private val RIFF_2 = 0x46.toByte()
    private val RIFF_3 = 0x46.toByte()
    private val WEBP_0 = 0x57.toByte()
    private val WEBP_1 = 0x45.toByte()
    private val WEBP_2 = 0x42.toByte()
    private val WEBP_3 = 0x50.toByte()
}
