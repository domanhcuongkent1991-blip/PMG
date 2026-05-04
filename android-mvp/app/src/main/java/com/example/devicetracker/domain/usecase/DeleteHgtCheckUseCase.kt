package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.HgtCheckRepository
import javax.inject.Inject

class DeleteHgtCheckUseCase @Inject constructor(
    private val repository: HgtCheckRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteCheck(id)
    }
}
