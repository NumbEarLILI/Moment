package com.example.moment.data.nas

internal fun shouldRefreshNasDiaryImages(
    localImageReferenceCount: Int,
    remoteImageCount: Int,
    hasUnreadableLocalImage: Boolean = false
): Boolean {
    if (remoteImageCount <= 0) return false
    return localImageReferenceCount < remoteImageCount || hasUnreadableLocalImage
}
