package com.example.moment.domain.repository

import com.example.moment.domain.model.RecognizedImageLabel

interface ImageLabelAnalyzer {
    suspend fun labelsForImage(imageUri: String): List<RecognizedImageLabel>
}
