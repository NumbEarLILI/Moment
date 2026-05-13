package com.example.moment.domain.usecase

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
import javax.inject.Inject

class GetFragmentByIdUseCase @Inject constructor(
    private val repository: FragmentRepository
) {
    suspend operator fun invoke(id: Long): LifeFragment? = repository.getFragmentById(id)
}
