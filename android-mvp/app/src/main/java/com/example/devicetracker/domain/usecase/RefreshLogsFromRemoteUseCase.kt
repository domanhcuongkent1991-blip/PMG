package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class RefreshLogsFromRemoteUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshFromRemote()
}

