package com.example.moment.data.nas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasArchiveImageRefreshPolicyTest {

    @Test
    fun refreshesWhenRemoteHasMoreImagesThanLocalReferences() {
        assertTrue(shouldRefreshNasDiaryImages(localImageReferenceCount = 0, remoteImageCount = 2))
    }

    @Test
    fun doesNotRefreshWhenRemoteHasNoImages() {
        assertFalse(shouldRefreshNasDiaryImages(localImageReferenceCount = 0, remoteImageCount = 0))
    }

    @Test
    fun refreshesWhenLocalReferencesExistButAFileIsMissing() {
        assertTrue(
            shouldRefreshNasDiaryImages(
                localImageReferenceCount = 2,
                remoteImageCount = 2,
                hasUnreadableLocalImage = true
            )
        )
    }
}
