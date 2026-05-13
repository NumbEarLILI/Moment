package com.example.moment.domain.usecase

import com.example.moment.domain.caption.ImageLabelsCaptionMapper
import com.example.moment.domain.model.MomentCaptionSuggestion
import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.RecognizedImageLabel
import com.example.moment.domain.repository.ImageLabelAnalyzer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SuggestMomentCaptionFromImagesUseCase @Inject constructor(
    private val imageLabelAnalyzer: ImageLabelAnalyzer
) {
    suspend operator fun invoke(
        imageUris: List<String>,
        currentMood: Mood?
    ): MomentCaptionSuggestion = withContext(Dispatchers.IO) {
        val limited = imageUris.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
        val collected = mutableListOf<RecognizedImageLabel>()
        for (uri in limited) {
            runCatching { imageLabelAnalyzer.labelsForImage(uri) }
                .onSuccess { collected += it }
        }
        ImageLabelsCaptionMapper.buildSuggestion(collected, currentMood)
    }
}
