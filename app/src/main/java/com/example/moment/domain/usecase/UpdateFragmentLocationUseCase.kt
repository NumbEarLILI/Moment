package com.example.moment.domain.usecase

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.repository.FragmentRepository
import java.time.Clock
import javax.inject.Inject

class UpdateFragmentLocationUseCase @Inject constructor(
    private val repository: FragmentRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(fragmentStableId: String, location: FragmentLocation): Boolean {
        if (fragmentStableId.isBlank()) return false
        val existing = repository.getFragmentByStableId(fragmentStableId) ?: return false
        repository.updateFragment(
            existing.copy(
                location = location,
                updatedAt = clock.instant()
            )
        )
        return true
    }
}
