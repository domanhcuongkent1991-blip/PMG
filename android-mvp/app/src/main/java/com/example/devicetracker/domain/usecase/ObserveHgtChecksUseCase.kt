package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.domain.repository.HgtCheckRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveHgtChecksUseCase @Inject constructor(
    private val repository: HgtCheckRepository
) {
    operator fun invoke(query: String): Flow<List<HgtCheck>> = repository.observeChecks(query)
}
