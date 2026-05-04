package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class SaveDeviceLogUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    suspend operator fun invoke(log: DeviceLog) = repository.saveLog(log)
}
