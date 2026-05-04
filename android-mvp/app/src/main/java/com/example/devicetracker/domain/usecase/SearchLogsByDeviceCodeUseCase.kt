package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class SearchLogsByDeviceCodeUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    operator fun invoke(deviceCode: String, filter: RepairFilter) =
        repository.observeLogs(deviceCode.trim(), filter)
}
