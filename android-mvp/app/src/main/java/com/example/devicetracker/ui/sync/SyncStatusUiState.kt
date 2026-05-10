package com.example.devicetracker.ui.sync

import com.example.devicetracker.domain.model.PendingSyncItem

data class SyncStatusUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncMode: SyncExecutionMode = SyncExecutionMode.FULL,
    val totalLogs: Int = 0,
    val syncedLogs: Int = 0,
    val pendingLogs: Int = 0,
    val totalDmbtLogs: Int = 0,
    val syncedDmbtLogs: Int = 0,
    val pendingDmbtLogs: Int = 0,
    val totalHgtChecks: Int = 0,
    val syncedHgtChecks: Int = 0,
    val pendingHgtChecks: Int = 0,
    val queueSize: Int = 0,
    val queueErrorCount: Int = 0,
    val pendingItems: List<PendingSyncItem> = emptyList(),
    val sheetIssueItems: List<SyncDataIssueUiItem> = emptyList(),
    val ignoringItemId: String? = null,
    val latestQueueError: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

data class SyncDataIssueUiItem(
    val typeLabel: String,
    val sheetTitle: String,
    val deviceCode: String,
    val discoveryDate: String,
    val description: String,
    val rowNumbers: String
)
