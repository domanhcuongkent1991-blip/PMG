package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.HgtCheckRepository
import javax.inject.Inject

class SyncPendingHgtChecksUseCase @Inject constructor(
    private val repository: HgtCheckRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncPending()
}
