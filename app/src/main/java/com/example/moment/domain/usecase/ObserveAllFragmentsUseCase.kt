package com.example.moment.domain.usecase

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.isNasGhostPlaceholder
import com.example.moment.domain.repository.FragmentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveAllFragmentsUseCase @Inject constructor(
    private val repository: FragmentRepository
) {
    operator fun invoke(): Flow<List<LifeFragment>> =
        repository.observeAllFragments()
            .map { fragments -> fragments.filterNot { it.isNasGhostPlaceholder() } }
}
