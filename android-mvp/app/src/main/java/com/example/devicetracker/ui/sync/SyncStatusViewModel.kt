package com.example.devicetracker.ui.sync

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.data.remote.SheetsRemoteDataSource
import com.example.devicetracker.data.repository.DeviceLogRepositoryImpl
import com.example.devicetracker.domain.model.SyncOverview
import com.example.devicetracker.domain.usecase.GetSyncOverviewUseCase
import com.example.devicetracker.domain.usecase.RefreshHgtChecksFromRemoteUseCase
import com.example.devicetracker.domain.usecase.RefreshLogsFromRemoteUseCase
import com.example.devicetracker.domain.usecase.SyncPendingHgtChecksUseCase
import com.example.devicetracker.domain.usecase.SyncPendingLogsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val getSyncOverviewUseCase: GetSyncOverviewUseCase,
    private val syncPendingLogsUseCase: SyncPendingLogsUseCase,
    private val refreshLogsFromRemoteUseCase: RefreshLogsFromRemoteUseCase,
    private val syncPendingHgtChecksUseCase: SyncPendingHgtChecksUseCase,
    private val refreshHgtChecksFromRemoteUseCase: RefreshHgtChecksFromRemoteUseCase,
    private val deviceLogRepositoryImpl: DeviceLogRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncStatusUiState())
    val uiState: StateFlow<SyncStatusUiState> = _uiState.asStateFlow()

    init {
        refreshOverview()
    }

    fun refreshOverview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { getSyncOverviewUseCase() }
                .onSuccess { overview ->
                    _uiState.update {
                        it.copyFromOverview(
                            overview = overview,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = toFriendlySyncMessage(
                                throwable.message ?: "Không thể tải trạng thái đồng bộ."
                            )
                        )
                    }
                }
        }
    }

    fun selectSyncMode(mode: SyncExecutionMode) {
        _uiState.update {
            if (it.isSyncing || it.syncMode == mode) it else it.copy(syncMode = mode)
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            val mode = _uiState.value.syncMode
            val before = runCatching { getSyncOverviewUseCase() }.getOrNull()
            _uiState.update { it.copy(isSyncing = true, infoMessage = null, errorMessage = null) }

            val startedAt = SystemClock.elapsedRealtime()
            val pushResult = syncPendingLogsUseCase()
            Log.i(TAG, "syncNow step=pushLogs success=${pushResult.isSuccess}")
            val pushHgtResult = if (pushResult.isSuccess) {
                syncPendingHgtChecksUseCase()
            } else {
                Result.failure(pushResult.exceptionOrNull() ?: IllegalStateException("Unknown sync error"))
            }
            Log.i(TAG, "syncNow step=pushHgt success=${pushHgtResult.isSuccess}")
            val pullResult = if (pushHgtResult.isSuccess && mode == SyncExecutionMode.FULL) {
                Log.i(TAG, "syncNow step=pullLogs start mode=$mode")
                runSyncStepWithTimeout(step = "pullLogs") { refreshLogsFromRemoteUseCase() }
            } else if (pushHgtResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(pushHgtResult.exceptionOrNull() ?: IllegalStateException("Unknown sync error"))
            }
            Log.i(TAG, "syncNow step=pullLogs mode=$mode success=${pullResult.isSuccess}")
            val pullHgtResult = if (pullResult.isSuccess && mode == SyncExecutionMode.FULL) {
                Log.i(TAG, "syncNow step=pullHgt start mode=$mode")
                runSyncStepWithTimeout(step = "pullHgt") { refreshHgtChecksFromRemoteUseCase() }
            } else if (pullResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(pullResult.exceptionOrNull() ?: IllegalStateException("Unknown sync error"))
            }
            Log.i(TAG, "syncNow step=pullHgt mode=$mode success=${pullHgtResult.isSuccess}")

            val afterResult = runCatching { getSyncOverviewUseCase() }

            afterResult.onSuccess { overview ->
                _uiState.update {
                    it.copyFromOverview(
                        overview = overview,
                        isSyncing = false,
                        isLoading = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        isLoading = false,
                        errorMessage = toFriendlySyncMessage(
                            throwable.message ?: "Không thể cập nhật trạng thái sau khi đồng bộ."
                        )
                    )
                }
            }

            if (pushResult.isSuccess && pushHgtResult.isSuccess && pullResult.isSuccess && pullHgtResult.isSuccess) {
                val after = afterResult.getOrNull()
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(TAG, "syncNow success mode=$mode elapsedMs=$elapsedMs")
                _uiState.update {
                    it.copy(
                        infoMessage = buildSuccessMessage(before, after, mode, elapsedMs)
                    )
                }
            } else {
                val error = pullHgtResult.exceptionOrNull()?.message
                    ?: pullResult.exceptionOrNull()?.message
                    ?: pushHgtResult.exceptionOrNull()?.message
                    ?: pushResult.exceptionOrNull()?.message
                    ?: "Đồng bộ thất bại. Vui lòng thử lại."
                Log.e(TAG, "syncNow failed mode=$mode error=$error")
                _uiState.update {
                    it.copy(errorMessage = toFriendlySyncMessage(error))
                }
            }
        }
    }

    fun ignoreAmbiguousPending(itemId: String) {
        if (!itemId.startsWith("log:")) return
        val recordId = itemId.removePrefix("log:").trim()
        if (recordId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(ignoringItemId = itemId, errorMessage = null, infoMessage = null) }
            val ignored = runCatching { deviceLogRepositoryImpl.ignoreAmbiguousPendingRecord(recordId) }
                .getOrDefault(false)
            if (ignored) {
                _uiState.update {
                    it.copy(
                        ignoringItemId = null,
                        infoMessage = "Đã bỏ qua an toàn mục pending ambiguous: $recordId"
                    )
                }
                refreshOverview()
            } else {
                _uiState.update {
                    it.copy(
                        ignoringItemId = null,
                        errorMessage = "Không thể bỏ qua mục này vì không khớp điều kiện ambiguous an toàn."
                    )
                }
            }
        }
    }

    private fun buildSuccessMessage(
        before: SyncOverview?,
        after: SyncOverview?,
        mode: SyncExecutionMode,
        elapsedMs: Long
    ): String {
        val modeLabel = if (mode == SyncExecutionMode.QUICK) "nhanh" else "đầy đủ"
        val seconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        if (after == null) return "Đồng bộ thành công."
        if (before != null && didTotalsChange(before, after)) {
            return "Đồng bộ $modeLabel xong trong ${seconds}s: " +
                "DMBT ${before.totalDmbtLogs}→${after.totalDmbtLogs}, " +
                "HGT ${before.totalHgtChecks}→${after.totalHgtChecks}, " +
                "tổng ${before.totalLogs}→${after.totalLogs}."
        }
        if ((before?.queueSize ?: 0) == 0 && (before?.pendingLogs ?: 0) == 0) {
            return "Không có thiết bị chờ đồng bộ. Dữ liệu local đã sạch."
        }
        if (before == null) {
            return "Đồng bộ $modeLabel thành công trong ${seconds}s. Còn ${after.pendingLogs} bản ghi chờ."
        }
        return "Đồng bộ $modeLabel xong trong ${seconds}s: hàng đợi ${before.queueSize}→${after.queueSize}, pending ${before.pendingLogs}→${after.pendingLogs}."
    }

    private fun didTotalsChange(before: SyncOverview, after: SyncOverview): Boolean {
        return before.totalDmbtLogs != after.totalDmbtLogs ||
            before.totalHgtChecks != after.totalHgtChecks ||
            before.totalLogs != after.totalLogs
    }

    private fun SyncStatusUiState.copyFromOverview(
        overview: SyncOverview,
        isLoading: Boolean = this.isLoading,
        isSyncing: Boolean = this.isSyncing
    ): SyncStatusUiState {
        return copy(
            totalLogs = overview.totalLogs,
            syncedLogs = overview.syncedLogs,
            pendingLogs = overview.pendingLogs,
            totalDmbtLogs = overview.totalDmbtLogs,
            syncedDmbtLogs = overview.syncedDmbtLogs,
            pendingDmbtLogs = overview.pendingDmbtLogs,
            totalHgtChecks = overview.totalHgtChecks,
            syncedHgtChecks = overview.syncedHgtChecks,
            pendingHgtChecks = overview.pendingHgtChecks,
            queueSize = overview.queueSize,
            queueErrorCount = overview.queueErrorCount,
            pendingItems = overview.pendingItems,
            sheetIssueItems = latestSheetIssueItems(),
            latestQueueError = overview.latestQueueError?.let(::toFriendlySyncMessage),
            isLoading = isLoading,
            isSyncing = isSyncing
        )
    }

    private fun latestSheetIssueItems(): List<SyncDataIssueUiItem> {
        return deviceLogRepositoryImpl.getLatestDmbtSheetIssueReports().map { report ->
            SyncDataIssueUiItem(
                typeLabel = when (report.type) {
                    SheetsRemoteDataSource.DmbtSheetIssueType.DUPLICATE_IDENTITY -> "Trùng dữ liệu DMBT"
                    SheetsRemoteDataSource.DmbtSheetIssueType.INVALID_DISCOVERY_DATE -> "Thiếu/sai ngày phát hiện"
                },
                sheetTitle = report.sheetTitle,
                deviceCode = report.deviceCode,
                discoveryDate = report.discoveryDate,
                description = report.description,
                rowNumbers = report.rowNumbers.joinToString("/")
            )
        }
    }

    private suspend fun runSyncStepWithTimeout(
        step: String,
        block: suspend () -> Result<Unit>
    ): Result<Unit> {
        return try {
            withTimeout(SYNC_STEP_TIMEOUT_MS) { block() }
        } catch (_: TimeoutCancellationException) {
            Result.failure(
                IllegalStateException(
                    "Bước $step bị timeout sau ${SYNC_STEP_TIMEOUT_MS / 1000}s. Kiểm tra mạng hoặc token Google Sheets."
                )
            )
        }
    }

    companion object {
        private const val TAG = "SyncStatusViewModel"
        private const val SYNC_STEP_TIMEOUT_MS = 180_000L
    }
}
