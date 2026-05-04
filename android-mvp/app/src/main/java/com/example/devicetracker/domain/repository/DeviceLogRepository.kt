package com.example.devicetracker.domain.repository

import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.model.SyncOverview
import kotlinx.coroutines.flow.Flow

interface DeviceLogRepository {
    fun observeLogs(deviceCode: String, filter: RepairFilter): Flow<List<DeviceLog>>
    suspend fun getLog(recordId: String): DeviceLog?
    suspend fun getSyncOverview(): SyncOverview
    suspend fun saveLog(log: DeviceLog)
    suspend fun updateRepairDate(recordId: String, ngaySuaChua: String?, ghiChu: String)
    suspend fun syncPending(): Result<Unit>
    suspend fun refreshFromRemote(): Result<Unit>
}
