package com.example.moment.domain.usecase

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import javax.inject.Inject

class AddFragmentUseCase @Inject constructor(
    private val repository: FragmentRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        content: String,
        imageUris: List<String>,
        mood: Mood?,
        tags: List<String>
    ): AddFragmentResult {
        val normalizedContent = content.trim()
        val normalizedTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedContent.isEmpty() && imageUris.isEmpty()) {
            return AddFragmentResult.Empty
        }

        val now = clock.instant()
        val id = repository.addFragment(
            LifeFragment(
                content = normalizedContent,
                imageUris = imageUris,
                mood = mood,
                tags = normalizedTags,
                createdAt = now,
                updatedAt = now
            )
        )
        return AddFragmentResult.Saved(id)
    }
}

sealed interface AddFragmentResult {
    data object Empty : AddFragmentResult
    data class Saved(val id: Long) : AddFragmentResult
}
