package com.example.devicetracker.domain.model

data class SyncOverview(
    val totalLogs: Int,
    val syncedLogs: Int,
    val pendingLogs: Int,
    val totalDmbtLogs: Int,
    val syncedDmbtLogs: Int,
    val pendingDmbtLogs: Int,
    val totalHgtChecks: Int,
    val syncedHgtChecks: Int,
    val pendingHgtChecks: Int,
    val queueSize: Int,
    val queueErrorCount: Int,
    val latestQueueError: String?,
    val pendingItems: List<PendingSyncItem> = emptyList()
)
