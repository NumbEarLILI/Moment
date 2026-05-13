package com.example.moment.domain.usecase

import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.repository.FragmentRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveFragmentsForDateUseCase @Inject constructor(
    private val repository: FragmentRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<LifeFragment>> =
        repository.observeFragmentsForDate(date)
}
