package com.example.moment.data.nas

import com.example.moment.domain.model.UserAppPreferences

data class NasImageUploadMode(
    val uploadOriginal: Boolean
) {
    fun remoteFileName(index: Int, originalImageExtension: String? = null): String =
        "$index${if (uploadOriginal) normalizedImageExtension(originalImageExtension) else COMPRESSED_EXTENSION}"

    fun relativeImagePath(index: Int, originalImageExtension: String? = null): String =
        "images/${remoteFileName(index, originalImageExtension)}"

    fun contentType(originalMimeType: String?): String =
        if (uploadOriginal) {
            originalMimeType ?: ORIGINAL_CONTENT_TYPE
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

internal fun nasOriginalImageExtension(mimeType: String?, uriString: String): String {
    extensionFromImageMimeType(mimeType)?.let { return it }
    val lower = uriString.substringBefore('?').lowercase()
    return listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif")
        .firstOrNull { lower.endsWith(it) }
        ?.let { normalizedImageExtension(it) }
        ?: ".jpg"
}

private fun extensionFromImageMimeType(mimeType: String?): String? =
    when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/heic" -> ".heic"
        "image/heif" -> ".heif"
        else -> null
    }

private fun normalizedImageExtension(extension: String?): String =
    when (extension?.trim()?.lowercase()) {
        ".jpeg" -> ".jpg"
        ".jpg", ".png", ".gif", ".webp", ".heic", ".heif" -> extension.trim().lowercase()
        else -> ".jpg"
    }
