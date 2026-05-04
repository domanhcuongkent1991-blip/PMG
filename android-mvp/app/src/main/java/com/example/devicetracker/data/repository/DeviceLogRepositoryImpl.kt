package com.example.devicetracker.data.repository

import android.util.Log
import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.local.entity.SyncQueueEntity
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.model.toDomain
import com.example.devicetracker.data.model.toEntity
import com.example.devicetracker.data.remote.SheetsRemoteDataSource
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.PendingSyncItem
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.model.SyncOverview
import com.example.devicetracker.domain.repository.DeviceLogRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceLogRepositoryImpl @Inject constructor(
    private val deviceLogDao: DeviceLogDao,
    private val hgtCheckDao: HgtCheckDao,
    private val syncQueueDao: SyncQueueDao,
    private val remoteDataSource: SheetsRemoteDataSource
) : DeviceLogRepository {

    override fun observeLogs(deviceCode: String, filter: RepairFilter): Flow<List<DeviceLog>> {
        return deviceLogDao.observeByDeviceCode(deviceCode, filter.name)
            .map { items -> items.map { it.toDomain() } }
    }

    override suspend fun getLog(recordId: String): DeviceLog? {
        return deviceLogDao.getById(recordId)?.toDomain()
    }

    override suspend fun getSyncOverview(): SyncOverview {
        val totalDeviceLogs = deviceLogDao.countAll()
        val syncedDeviceLogs = deviceLogDao.countBySyncStatus("SYNCED")
        val pendingDeviceLogs = deviceLogDao.countBySyncStatus("PENDING")
        val totalHgtChecks = hgtCheckDao.countAll()
        val syncedHgtChecks = hgtCheckDao.countBySyncStatus("SYNCED")
        val pendingHgtChecks = hgtCheckDao.countBySyncStatus("PENDING")
        val queueItems = syncQueueDao.getAll()
        val queueSize = queueItems.size
        val queueErrorCount = syncQueueDao.countWithErrors()
        val latestQueueError = syncQueueDao.latestError()?.trim()?.ifBlank { null }
        val pendingItems = buildPendingItems(queueItems)
        return SyncOverview(
            totalLogs = totalDeviceLogs + totalHgtChecks,
            syncedLogs = syncedDeviceLogs + syncedHgtChecks,
            pendingLogs = pendingDeviceLogs + pendingHgtChecks,
            totalDmbtLogs = totalDeviceLogs,
            syncedDmbtLogs = syncedDeviceLogs,
            pendingDmbtLogs = pendingDeviceLogs,
            totalHgtChecks = totalHgtChecks,
            syncedHgtChecks = syncedHgtChecks,
            pendingHgtChecks = pendingHgtChecks,
            queueSize = queueSize,
            queueErrorCount = queueErrorCount,
            latestQueueError = latestQueueError,
            pendingItems = pendingItems
        )
    }

    override suspend fun saveLog(log: DeviceLog) {
        deviceLogDao.upsert(log.toEntity(syncStatus = "PENDING"))
        enqueueUpsert(log.recordId)
        Log.i(TAG, "saveLog queued recordId=${log.recordId}")
    }

    override suspend fun updateRepairDate(recordId: String, ngaySuaChua: String?, ghiChu: String) {
        val existing = deviceLogDao.getById(recordId)
            ?: throw IllegalArgumentException("Record not found: $recordId")

        val normalizedRepairDate = ngaySuaChua?.trim()?.ifBlank { null }
        val normalizedNote = ghiChu.trim()
        deviceLogDao.upsert(
            existing.copy(
                ngaySuaChua = normalizedRepairDate,
                ghiChu = normalizedNote,
                updatedAt = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
        )
        enqueueUpsert(recordId)
        Log.i(TAG, "updateRepairDate queued recordId=$recordId")
    }

    override suspend fun syncPending(): Result<Unit> {
        val queueSnapshot = syncQueueDao.getAll().filter { it.operation == OP_UPSERT_LOG }
        if (queueSnapshot.isEmpty()) {
            Log.d(TAG, "syncPending skipped: queue is empty")
            return Result.success(Unit)
        }

        val syncCandidateLogs = queueSnapshot
            .map { it.recordId }
            .distinct()
            .mapNotNull { recordId -> deviceLogDao.getById(recordId)?.toDomain() }
            .distinctBy { it.recordId }

        Log.i(
            TAG,
            "syncPending start: queueSnapshot=${queueSnapshot.size}, candidateLogs=${syncCandidateLogs.size}"
        )

        if (syncCandidateLogs.isEmpty()) {
            queueSnapshot.forEach { item -> syncQueueDao.deleteById(item.id) }
            Log.w(TAG, "syncPending cleared ${queueSnapshot.size} queue items with missing local rows")
            return Result.success(Unit)
        }

        val result = remoteDataSource.pushLogs(syncCandidateLogs)
        if (result.isSuccess) {
            var markedSyncedCount = 0
            var staleCount = 0
            syncCandidateLogs.forEach { pushedLog ->
                val current = deviceLogDao.getById(pushedLog.recordId)
                if (shouldMarkAsSynced(current, pushedLog)) {
                    val currentLog = current ?: return@forEach
                    deviceLogDao.upsert(currentLog.copy(syncStatus = "SYNCED"))
                    markedSyncedCount += 1
                } else {
                    staleCount += 1
                }
            }
            // Delete only the queue snapshot consumed by this sync run.
            // New queue rows created during sync stay intact for the next run.
            queueSnapshot.forEach { item -> syncQueueDao.deleteById(item.id) }
            Log.i(
                TAG,
                "syncPending success: queueDeleted=${queueSnapshot.size}, markedSynced=$markedSyncedCount, staleLocal=$staleCount"
            )
            return Result.success(Unit)
        }

        val message = result.exceptionOrNull()?.message ?: "Unknown sync error"
        queueSnapshot.forEach { item -> syncQueueDao.markFailed(item.id, message) }
        Log.e(TAG, "syncPending failed: queueMarkedFailed=${queueSnapshot.size}, error=$message")
        return result
    }

    override suspend fun refreshFromRemote(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "refreshFromRemote start")
            val result = try {
                withTimeout(REMOTE_PULL_TIMEOUT_MS) { remoteDataSource.pullLatestLogs() }
            } catch (_: TimeoutCancellationException) {
                Result.failure(
                    IOException(
                        "Hết thời gian chờ kéo dữ liệu DMBT từ Google Sheet sau ${REMOTE_PULL_TIMEOUT_MS / 1000}s."
                    )
                )
            }
            result.onSuccess { logs ->
                var appliedCount = 0
                var skippedCount = 0
                val entitiesToUpsert = mutableListOf<DeviceLogEntity>()
                logs.forEach { remoteLog ->
                    val local = deviceLogDao.getById(remoteLog.recordId)
                    if (shouldApplyRemoteLog(local, remoteLog)) {
                        entitiesToUpsert += remoteLog.toEntity(syncStatus = "SYNCED")
                        appliedCount += 1
                    } else {
                        skippedCount += 1
                    }
                }
                if (entitiesToUpsert.isNotEmpty()) {
                    deviceLogDao.upsertAll(entitiesToUpsert)
                }
                Log.i(
                    TAG,
                    "refreshFromRemote success: fetched=${logs.size}, applied=$appliedCount, skipped=$skippedCount"
                )
            }.onFailure { throwable ->
                Log.e(TAG, "refreshFromRemote failed: ${throwable.message}")
            }
            result.map { Unit }
        }
    }

    private suspend fun enqueueUpsert(recordId: String) {
        syncQueueDao.deleteByRecordId(recordId)
        syncQueueDao.insert(
            SyncQueueEntity(
                recordId = recordId,
                operation = OP_UPSERT_LOG,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun buildPendingItems(queueItems: List<SyncQueueEntity>): List<PendingSyncItem> {
        val localLogItems = deviceLogDao.getPendingLogs().map { entity ->
            PendingSyncItem(
                id = "log:${entity.recordId}",
                deviceCode = entity.maThietBi.ifBlank { entity.recordId },
                typeLabel = "DMBT",
                detail = "Chờ đẩy thay đổi lên Google Sheet",
                syncStatus = entity.syncStatus,
                updatedAt = entity.updatedAt
            )
        }
        val localHgtItems = hgtCheckDao.getPendingChecks().map { entity ->
            PendingSyncItem(
                id = "hgt:${entity.id}",
                deviceCode = entity.maThietBi.ifBlank { entity.id },
                typeLabel = "HGT",
                detail = "Chờ đẩy thay đổi lên Google Sheet",
                syncStatus = entity.syncStatus,
                updatedAt = entity.updatedAt
            )
        }
        val localIds = (localLogItems + localHgtItems).map { it.id }.toSet()
        val queueOnlyItems = mutableListOf<PendingSyncItem>()
        for (item in queueItems) {
            item.toPendingItemIfMissing(localIds)?.let { queueOnlyItems.add(it) }
        }

        return (localLogItems + localHgtItems + queueOnlyItems)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
            .take(MAX_PENDING_ITEMS)
    }

    private suspend fun SyncQueueEntity.toPendingItemIfMissing(localIds: Set<String>): PendingSyncItem? {
        return when (operation) {
            OP_UPSERT_LOG -> {
                val itemId = "log:$recordId"
                if (itemId in localIds) return null
                val local = deviceLogDao.getById(recordId)
                PendingSyncItem(
                    id = itemId,
                    deviceCode = local?.maThietBi?.ifBlank { recordId } ?: recordId,
                    typeLabel = "DMBT",
                    detail = lastError?.takeIf { it.isNotBlank() } ?: "Đang nằm trong hàng đợi đồng bộ",
                    syncStatus = local?.syncStatus ?: "QUEUED",
                    updatedAt = local?.updatedAt ?: createdAt
                )
            }
            OP_UPSERT_HGT -> {
                if (!recordId.startsWith(HGT_UPSERT_PREFIX)) return null
                val checkId = recordId.removePrefix(HGT_UPSERT_PREFIX)
                val itemId = "hgt:$checkId"
                if (itemId in localIds) return null
                val local = hgtCheckDao.getById(checkId)
                PendingSyncItem(
                    id = itemId,
                    deviceCode = local?.maThietBi?.ifBlank { checkId } ?: checkId,
                    typeLabel = "HGT",
                    detail = lastError?.takeIf { it.isNotBlank() } ?: "Đang nằm trong hàng đợi đồng bộ",
                    syncStatus = local?.syncStatus ?: "QUEUED",
                    updatedAt = local?.updatedAt ?: createdAt
                )
            }
            OP_DELETE_HGT -> {
                if (!recordId.startsWith(HGT_DELETE_PREFIX)) return null
                val deviceCode = recordId.removePrefix(HGT_DELETE_PREFIX)
                if (deviceCode.isBlank()) return null
                PendingSyncItem(
                    id = "hgt-delete:$deviceCode",
                    deviceCode = deviceCode.uppercase(),
                    typeLabel = "Xóa HGT",
                    detail = lastError?.takeIf { it.isNotBlank() } ?: "Chờ xóa trên Google Sheet",
                    syncStatus = "QUEUED",
                    updatedAt = createdAt
                )
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "DeviceLogRepository"
        private const val REMOTE_PULL_TIMEOUT_MS = 180_000L
        private const val OP_UPSERT_LOG = "UPSERT_LOG"
        private const val OP_UPSERT_HGT = "UPSERT_HGT"
        private const val OP_DELETE_HGT = "DELETE_HGT"
        private const val HGT_UPSERT_PREFIX = "hgt-upsert:"
        private const val HGT_DELETE_PREFIX = "hgt-delete:"
        private const val MAX_PENDING_ITEMS = 30
    }
}

internal fun shouldMarkAsSynced(currentLocal: DeviceLogEntity?, pushedLog: DeviceLog): Boolean {
    if (currentLocal == null) return false
    return currentLocal.updatedAt == pushedLog.updatedAt
}

internal fun shouldApplyRemoteLog(currentLocal: DeviceLogEntity?, remoteLog: DeviceLog): Boolean {
    if (currentLocal == null) return true
    if (currentLocal.syncStatus != "SYNCED") return false
    return remoteLog.updatedAt >= currentLocal.updatedAt
}
