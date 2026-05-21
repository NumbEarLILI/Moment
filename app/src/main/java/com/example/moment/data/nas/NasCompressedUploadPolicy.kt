package com.example.moment.data.nas

internal enum class NasCompressedUploadFailure {
    GENERATION_FAILED,
    PUT_FAILED
}

internal fun shouldFallbackToOriginalAfterCompressedFailure(
    failure: NasCompressedUploadFailure
): Boolean =
    failure == NasCompressedUploadFailure.GENERATION_FAILED
