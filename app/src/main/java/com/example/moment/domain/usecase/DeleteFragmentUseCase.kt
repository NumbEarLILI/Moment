package com.example.moment.domain.usecase

import com.example.moment.domain.repository.FragmentRepository
import javax.inject.Inject

class DeleteFragmentUseCase @Inject constructor(
    private val repository: FragmentRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteFragment(id)
    }
}
