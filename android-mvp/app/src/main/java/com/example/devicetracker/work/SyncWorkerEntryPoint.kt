package com.example.devicetracker.work

import com.example.devicetracker.domain.repository.DeviceLogRepository
import com.example.devicetracker.domain.repository.HgtCheckRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun deviceLogRepository(): DeviceLogRepository
    fun hgtCheckRepository(): HgtCheckRepository
}
