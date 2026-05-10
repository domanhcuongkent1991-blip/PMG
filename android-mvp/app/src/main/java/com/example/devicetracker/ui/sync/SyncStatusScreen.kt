package com.example.devicetracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devicetracker.R
import com.example.devicetracker.domain.model.PendingSyncItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sync_status_mode_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (uiState.syncMode == SyncExecutionMode.QUICK) {
                    stringResource(R.string.sync_status_mode_selected_quick)
                } else {
                    stringResource(R.string.sync_status_mode_selected_full)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isQuickSelected = uiState.syncMode == SyncExecutionMode.QUICK
                val isFullSelected = uiState.syncMode == SyncExecutionMode.FULL
                if (isQuickSelected) {
                    Button(
                        onClick = { viewModel.selectSyncMode(SyncExecutionMode.QUICK) },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(stringResource(R.string.sync_status_mode_quick))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.selectSyncMode(SyncExecutionMode.QUICK) },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sync_status_mode_quick))
                    }
                }
                if (isFullSelected) {
                    Button(
                        onClick = { viewModel.selectSyncMode(SyncExecutionMode.FULL) },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(stringResource(R.string.sync_status_mode_full))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.selectSyncMode(SyncExecutionMode.FULL) },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sync_status_mode_full))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::refreshOverview,
                    enabled = !uiState.isLoading && !uiState.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.sync_status_refresh))
                }
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !uiState.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        if (uiState.syncMode == SyncExecutionMode.QUICK) {
                            stringResource(R.string.sync_status_sync_now_quick)
                        } else {
                            stringResource(R.string.sync_status_sync_now_full)
                        }
                    )
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!uiState.infoMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = uiState.infoMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            SyncMetricCard(
                title = stringResource(R.string.sync_status_total_logs),
                value = uiState.totalLogs.toString()
            )
            SyncMetricCard(
                title = stringResource(R.string.sync_status_synced_logs),
                value = uiState.syncedLogs.toString()
            )
            SyncMetricCard(
                title = stringResource(R.string.sync_status_pending_logs),
                value = uiState.pendingLogs.toString()
            )
            Text(
                text = stringResource(R.string.sync_status_breakdown_title),
                style = MaterialTheme.typography.titleMedium
            )
            SyncBreakdownCard(
                title = stringResource(R.string.sync_status_dmbt_title),
                total = uiState.totalDmbtLogs,
                synced = uiState.syncedDmbtLogs,
                pending = uiState.pendingDmbtLogs
            )
            SyncBreakdownCard(
                title = stringResource(R.string.sync_status_hgt_title),
                total = uiState.totalHgtChecks,
                synced = uiState.syncedHgtChecks,
                pending = uiState.pendingHgtChecks
            )
            SyncMetricCard(
                title = stringResource(R.string.sync_status_queue_size),
                value = uiState.queueSize.toString()
            )
            SyncMetricCard(
                title = stringResource(R.string.sync_status_queue_error_count),
                value = uiState.queueErrorCount.toString()
            )

            Text(
                text = stringResource(R.string.sync_status_sheet_issues_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.sheetIssueItems.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sync_status_sheet_issues_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                uiState.sheetIssueItems.forEach { item ->
                    SheetIssueCard(item = item)
                }
            }

            Text(
                text = stringResource(R.string.sync_status_pending_items_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.pendingItems.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sync_status_pending_items_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                uiState.pendingItems.forEach { item ->
                    val canIgnoreAmbiguous = item.id.startsWith("log:") &&
                        item.typeLabel == "DMBT" &&
                        item.detail.contains("Ambiguous DMBT fallback key for push")
                    PendingSyncItemCard(
                        item = item,
                        canIgnoreAmbiguous = canIgnoreAmbiguous,
                        isIgnoring = uiState.ignoringItemId == item.id,
                        onIgnoreAmbiguous = { viewModel.ignoreAmbiguousPending(item.id) }
                    )
                }
            }

            if (!uiState.latestQueueError.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sync_status_latest_error),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = uiState.latestQueueError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetIssueCard(item: SyncDataIssueUiItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.typeLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = item.sheetTitle,
                style = MaterialTheme.typography.labelLarge
            )
            if (item.deviceCode.isNotBlank()) {
                Text(
                    text = item.deviceCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (item.discoveryDate.isNotBlank()) {
                Text(
                    text = stringResource(R.string.sync_status_sheet_issue_date, item.discoveryDate),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (item.description.isNotBlank()) {
                Text(
                    text = stringResource(R.string.sync_status_sheet_issue_detail, item.description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(R.string.sync_status_sheet_issue_rows, item.rowNumbers),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PendingSyncItemCard(
    item: PendingSyncItem,
    canIgnoreAmbiguous: Boolean,
    isIgnoring: Boolean,
    onIgnoreAmbiguous: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.deviceCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.typeLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.syncStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            if (canIgnoreAmbiguous) {
                OutlinedButton(
                    onClick = onIgnoreAmbiguous,
                    enabled = !isIgnoring,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (isIgnoring) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(stringResource(R.string.sync_status_ignore_ambiguous))
                }
            }
        }
    }
}

@Composable
private fun SyncBreakdownCard(
    title: String,
    total: Int,
    synced: Int,
    pending: Int
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            SyncBreakdownRow(
                label = stringResource(R.string.sync_status_split_total),
                value = total
            )
            SyncBreakdownRow(
                label = stringResource(R.string.sync_status_split_synced),
                value = synced
            )
            SyncBreakdownRow(
                label = stringResource(R.string.sync_status_split_pending),
                value = pending
            )
        }
    }
}

@Composable
private fun SyncBreakdownRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SyncMetricCard(title: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
