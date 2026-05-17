package com.example.moment.data.nas

import com.example.moment.domain.model.UserAppPreferences

data class NasImageUploadMode(
    val uploadOriginal: Boolean
) {
    fun remoteFileName(index: Int): String =
        "$index${if (uploadOriginal) ORIGINAL_EXTENSION else COMPRESSED_EXTENSION}"

    fun relativeImagePath(index: Int): String = "images/${remoteFileName(index)}"

    fun contentType(originalMimeType: String?): String =
        if (uploadOriginal) {
            originalMimeType ?: ORIGINAL_CONTENT_TYPE
        } else {
            COMPRESSED_CONTENT_TYPE
        }

    companion object {
        private const val ORIGINAL_EXTENSION = ".bin"
        private const val COMPRESSED_EXTENSION = ".jpg"
        private const val ORIGINAL_CONTENT_TYPE = "application/octet-stream"
        private const val COMPRESSED_CONTENT_TYPE = "image/jpeg"

        val COMPRESSED = NasImageUploadMode(uploadOriginal = false)

        fun fromPreferences(preferences: UserAppPreferences): NasImageUploadMode =
            NasImageUploadMode(uploadOriginal = preferences.uploadOriginalImagesToNas)
    }
}
