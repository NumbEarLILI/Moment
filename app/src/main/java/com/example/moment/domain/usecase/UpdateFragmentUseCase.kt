package com.example.moment.domain.usecase

import com.example.moment.domain.model.Mood
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import javax.inject.Inject

class UpdateFragmentUseCase @Inject constructor(
    private val repository: FragmentRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        id: Long,
        content: String,
        imageUris: List<String>,
        mood: Mood?,
        tags: List<String>
    ): UpdateFragmentResult {
        val existing = repository.getFragmentById(id) ?: return UpdateFragmentResult.NotFound

        val normalizedContent = content.trim()
        val normalizedTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedContent.isEmpty() && imageUris.isEmpty()) {
            return UpdateFragmentResult.Empty
        }

        val now = clock.instant()
        repository.updateFragment(
            existing.copy(
                content = normalizedContent,
                imageUris = imageUris,
                mood = mood,
                tags = normalizedTags,
                updatedAt = now
            )
        )
        return UpdateFragmentResult.Saved
    }
}

sealed interface UpdateFragmentResult {
    data object Empty : UpdateFragmentResult
    data object NotFound : UpdateFragmentResult
    data object Saved : UpdateFragmentResult
}
