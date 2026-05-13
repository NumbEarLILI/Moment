package com.example.moment.data.vision

import android.content.Context
import android.net.Uri
import com.example.moment.domain.model.RecognizedImageLabel
import com.example.moment.domain.repository.ImageLabelAnalyzer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class MlKitImageLabelAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageLabelAnalyzer {

    private val client by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun labelsForImage(imageUri: String): List<RecognizedImageLabel> {
        val uri = Uri.parse(imageUri)
        val input = InputImage.fromMediaUri(context, uri)
        return client.process(input).await().map { label ->
            RecognizedImageLabel(text = label.text, confidence = label.confidence)
        }
    }
}
