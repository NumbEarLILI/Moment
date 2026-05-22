package com.example.moment.data.nas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasCompressedUploadPolicyTest {

    @Test
    fun fallsBackToOriginalOnlyWhenCompressedFileGenerationFailed() {
        assertTrue(
            shouldFallbackToOriginalAfterCompressedFailure(
                NasCompressedUploadFailure.GENERATION_FAILED
            )
        )
        assertFalse(
            shouldFallbackToOriginalAfterCompressedFailure(
                NasCompressedUploadFailure.PUT_FAILED
            )
        )
    }
}
