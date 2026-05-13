package com.example.moment.domain.caption

import com.example.moment.domain.model.Mood
import com.example.moment.domain.model.RecognizedImageLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLabelsCaptionMapperTest {

    @Test
    fun emptyLabels_returnsFallbackCopy() {
        val result = ImageLabelsCaptionMapper.buildSuggestion(emptyList(), null)
        assertTrue(result.suggestedContent.isNotBlank())
        assertTrue(result.suggestedTags.isNotEmpty())
        assertNull(result.suggestedMood)
    }

    @Test
    fun knownLabels_buildsChineseSentenceAndTags() {
        val labels = listOf(
            RecognizedImageLabel("Food", 0.9f),
            RecognizedImageLabel("Table", 0.7f)
        )
        val result = ImageLabelsCaptionMapper.buildSuggestion(labels, null)
        assertTrue(result.suggestedContent.contains("美食"))
        assertTrue(result.suggestedContent.contains("桌子"))
        assertTrue(result.suggestedTags.any { it == "美食" })
    }

    @Test
    fun unknownLabels_usesEnglishTokensInSentence() {
        val labels = listOf(
            RecognizedImageLabel("Quasar", 0.95f),
            RecognizedImageLabel("Nebula", 0.8f)
        )
        val result = ImageLabelsCaptionMapper.buildSuggestion(labels, null)
        assertTrue(result.suggestedContent.contains("Quasar"))
        assertTrue(result.suggestedTags.any { it == "Quasar" || it == "Nebula" })
    }

    @Test
    fun infersHappyMoodFromPartyWhenNoMoodSet() {
        val labels = listOf(RecognizedImageLabel("Party", 0.92f))
        val result = ImageLabelsCaptionMapper.buildSuggestion(labels, null)
        assertEquals(Mood.HAPPY, result.suggestedMood)
    }

    @Test
    fun doesNotOverrideExistingMood() {
        val labels = listOf(RecognizedImageLabel("Party", 0.92f))
        val result = ImageLabelsCaptionMapper.buildSuggestion(labels, Mood.CALM)
        assertEquals(Mood.CALM, result.suggestedMood)
    }
}
