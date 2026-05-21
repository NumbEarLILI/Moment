package com.example.moment.data.nas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasDiaryUploadResultPolicyTest {

    @Test
    fun singleDiaryUploadFailsWhenAnyImageWasSkipped() {
        assertTrue(shouldFailSingleDiaryUploadForSkippedImages(imagesSkipped = 1))
    }

    @Test
    fun singleDiaryUploadSucceedsWhenNoImagesWereSkipped() {
        assertFalse(shouldFailSingleDiaryUploadForSkippedImages(imagesSkipped = 0))
    }
}
