package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.HgtCheckRepository
import javax.inject.Inject

class UpdateHgtLatestCheckDateUseCase @Inject constructor(
    private val repository: HgtCheckRepository
) {
    suspend operator fun invoke(id: String, latestDate: String) {
        repository.updateLatestCheckDate(id, latestDate)
    }
}
