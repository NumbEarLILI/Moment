package com.example.moment.data.nas

internal fun shouldFailSingleDiaryUploadForSkippedImages(imagesSkipped: Int): Boolean =
    imagesSkipped > 0
