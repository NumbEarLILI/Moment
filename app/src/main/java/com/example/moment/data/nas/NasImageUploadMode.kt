package com.example.moment.data.nas

import com.example.moment.domain.model.UserAppPreferences

data class NasImageUploadMode(
    val uploadOriginal: Boolean
) {
    fun remoteFileName(index: Int, originalImageExtension: String? = null): String =
        "$index${if (uploadOriginal) normalizedImageExtension(originalImageExtension) else COMPRESSED_EXTENSION}"

    fun relativeImagePath(index: Int, originalImageExtension: String? = null): String =
        "images/${remoteFileName(index, originalImageExtension)}"

    fun contentType(originalMimeType: String?, imageExtension: String? = null): String =
        if (uploadOriginal) {
            normalizedImageMimeType(originalMimeType) ?: nasImageContentType(imageExtension, ORIGINAL_CONTENT_TYPE)
        } else {
            COMPRESSED_CONTENT_TYPE
        }

    companion object {
        private const val COMPRESSED_EXTENSION = ".jpg"
        private const val ORIGINAL_CONTENT_TYPE = "application/octet-stream"
        private const val COMPRESSED_CONTENT_TYPE = "image/jpeg"

        val COMPRESSED = NasImageUploadMode(uploadOriginal = false)

        fun fromPreferences(preferences: UserAppPreferences): NasImageUploadMode =
            NasImageUploadMode(uploadOriginal = preferences.uploadOriginalImagesToNas)
    }
}

internal fun nasOriginalImageExtension(
    mimeType: String?,
    uriString: String,
    headerBytes: ByteArray? = null
): String {
    extensionFromImageMimeType(mimeType)?.let { return it }
    headerBytes?.let { nasImageExtensionFromBytes(it)?.let { ext -> return ext } }
    return nasImageExtensionFromPath(uriString)
        ?: ".jpg"
}

internal fun nasImageExtensionFromPath(path: String): String? {
    val lower = path.substringBefore('?').lowercase()
    return listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif")
        .firstOrNull { lower.endsWith(it) }
        ?.let { normalizedImageExtension(it) }
}

private fun extensionFromImageMimeType(mimeType: String?): String? =
    when (normalizedImageMimeType(mimeType)) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/heic" -> ".heic"
        "image/heif" -> ".heif"
        else -> null
    }

internal fun nasImageExtensionFromBytes(bytes: ByteArray): String? {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return ".jpg"
    if (bytes.size >= 4 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return ".png"
    }
    if (bytes.size >= 6 &&
        bytes[0] == 0x47.toByte() &&
        bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte()
    ) {
        return ".gif"
    }
    if (bytes.size >= 12 &&
        bytes[0] == 0x52.toByte() &&
        bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() &&
        bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() &&
        bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() &&
        bytes[11] == 0x50.toByte()
    ) {
        return ".webp"
    }
    if (bytes.size >= 12 &&
        bytes[4] == 0x66.toByte() &&
        bytes[5] == 0x74.toByte() &&
        bytes[6] == 0x79.toByte() &&
        bytes[7] == 0x70.toByte()
    ) {
        val brand = String(bytes.copyOfRange(8, 12), Charsets.ISO_8859_1)
        if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "heif")) return ".heic"
    }
    return null
}

internal fun nasImageContentType(extension: String?, fallback: String? = null): String =
    when (extension?.trim()?.lowercase()) {
        ".jpeg" -> "image/jpeg"
        ".jpg" -> "image/jpeg"
        ".png" -> "image/png"
        ".gif" -> "image/gif"
        ".webp" -> "image/webp"
        ".heic" -> "image/heic"
        ".heif" -> "image/heif"
        else -> fallback ?: "application/octet-stream"
    }

private fun normalizedImageMimeType(mimeType: String?): String? {
    val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return normalized.takeIf { it.startsWith("image/") && "*" !in it }
}

private fun normalizedImageExtension(extension: String?): String =
    when (extension?.trim()?.lowercase()) {
        ".jpeg" -> ".jpg"
        ".jpg", ".png", ".gif", ".webp", ".heic", ".heif" -> extension.trim().lowercase()
        else -> ".jpg"
    }
