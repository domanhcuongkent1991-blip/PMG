package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class SyncPendingLogsUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncPending()
}
