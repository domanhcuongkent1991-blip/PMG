package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.model.SyncOverview
import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class GetSyncOverviewUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    suspend operator fun invoke(): SyncOverview = repository.getSyncOverview()
}
