package com.example.devicetracker.data.repository

import android.util.Log
import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.local.entity.SyncQueueEntity
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.model.DmbtRepairUpdate
import com.example.devicetracker.data.model.toDomain
import com.example.devicetracker.data.model.toEntity
import com.example.devicetracker.data.remote.SheetsRemoteDataSource
import com.example.devicetracker.data.sheet.SheetConfig
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.PendingSyncItem
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.model.SyncOverview
import com.example.devicetracker.domain.repository.DeviceLogRepository
import com.example.devicetracker.util.DateTextFormatter
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
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
    private val remoteDataSource: SheetsRemoteDataSource,
    private val sheetConfig: SheetConfig
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

    override suspend fun updateRepairDate(
        recordId: String,
        ngaySuaChua: String?,
        ghiChu: String,
        tinhTrangThietBi: String
    ) {
        val existing = deviceLogDao.getById(recordId)
            ?: throw IllegalArgumentException("Record not found: $recordId")

        val normalizedRepairDate = ngaySuaChua?.trim()?.ifBlank { null }
        val normalizedNote = ghiChu.trim()
        val normalizedCondition = tinhTrangThietBi.trim()
        deviceLogDao.upsert(
            existing.copy(
                ngaySuaChua = normalizedRepairDate,
                ghiChu = normalizedNote,
                tinhTrangThietBi = normalizedCondition,
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
            .map { log -> log.withResolvedDmbtSourceSheetId() }
            .distinctBy { it.recordId }

        syncCandidateLogs.forEach { log ->
            if (log.sourceSheetId != null) {
                val current = deviceLogDao.getById(log.recordId)
                if (current != null && current.sourceSheetId == null) {
                    deviceLogDao.upsert(log.toEntity(syncStatus = current.syncStatus))
                }
            }
        }

        Log.i(
            TAG,
            "syncPending start: queueSnapshot=${queueSnapshot.size}, candidateLogs=${syncCandidateLogs.size}"
        )

        if (syncCandidateLogs.isEmpty()) {
            queueSnapshot.forEach { item -> syncQueueDao.deleteById(item.id) }
            Log.w(TAG, "syncPending cleared ${queueSnapshot.size} queue items with missing local rows")
            return Result.success(Unit)
        }

        val pushOutcome = pushLogsWithAmbiguousFallback(syncCandidateLogs)
        if (pushOutcome.successfulLogs.isNotEmpty()) {
            var markedSyncedCount = 0
            var staleCount = 0
            val recordsMarkedSynced = mutableSetOf<String>()

            pushOutcome.successfulLogs.forEach { pushedLog ->
                val current = deviceLogDao.getById(pushedLog.recordId)
                if (shouldMarkAsSynced(current, pushedLog)) {
                    val currentLog = current ?: return@forEach
                    deviceLogDao.upsert(
                        currentLog.copy(
                            sourceSheetId = pushedLog.sourceSheetId ?: currentLog.sourceSheetId,
                            syncStatus = "SYNCED"
                        )
                    )
                    markedSyncedCount += 1
                    recordsMarkedSynced.add(pushedLog.recordId)
                } else {
                    staleCount += 1
                }
            }
            // Chỉ xóa queue cho records đã mark SYNCED thành công.
            // Records có thay đổi local sau khi push sẽ giữ lại queue
            // để sync tiếp theo push bản mới nhất lên sheet.
            queueSnapshot.forEach { item ->
                if (item.recordId in recordsMarkedSynced) {
                    syncQueueDao.deleteById(item.id)
                }
            }
            markFailedQueueItems(queueSnapshot, pushOutcome.failuresByRecordId)
            Log.i(
                TAG,
                "syncPending success: queueDeleted=${recordsMarkedSynced.size}, keptQueue=${staleCount}, markedSynced=$markedSyncedCount, staleLocal=$staleCount, failed=${pushOutcome.failuresByRecordId.size}"
            )
            return Result.success(Unit)
        }

        val message = pushOutcome.batchError?.message ?: "Unknown sync error"
        queueSnapshot.forEach { item -> syncQueueDao.markFailed(item.id, message) }
        Log.e(TAG, "syncPending failed: queueMarkedFailed=${queueSnapshot.size}, error=$message")
        return Result.failure(pushOutcome.batchError ?: IOException(message))
    }

    private suspend fun pushLogsWithAmbiguousFallback(logs: List<DeviceLog>): PushLogsOutcome {
        val batchResult = remoteDataSource.pushLogs(logs)
        if (batchResult.isSuccess) {
            return PushLogsOutcome(successfulLogs = logs)
        }

        val batchError = batchResult.exceptionOrNull()
        if (!isAmbiguousDmbtFallbackError(batchError)) {
            return PushLogsOutcome(batchError = batchError)
        }

        val successfulLogs = mutableListOf<DeviceLog>()
        val failuresByRecordId = linkedMapOf<String, String>()
        logs.forEach { log ->
            val singleResult = remoteDataSource.pushLogs(listOf(log))
            if (singleResult.isSuccess) {
                successfulLogs += log
            } else {
                failuresByRecordId[log.recordId] =
                    singleResult.exceptionOrNull()?.message ?: "Unknown sync error"
            }
        }

        Log.w(
            TAG,
            "syncPending ambiguous fallback isolated: successful=${successfulLogs.size}, failed=${failuresByRecordId.size}"
        )
        return PushLogsOutcome(
            successfulLogs = successfulLogs,
            failuresByRecordId = failuresByRecordId,
            batchError = batchError
        )
    }

    private suspend fun markFailedQueueItems(
        queueSnapshot: List<SyncQueueEntity>,
        failuresByRecordId: Map<String, String>
    ) {
        if (failuresByRecordId.isEmpty()) return
        queueSnapshot.forEach { item ->
            failuresByRecordId[item.recordId]?.let { message ->
                syncQueueDao.markFailed(item.id, message)
            }
        }
    }

    private fun isAmbiguousDmbtFallbackError(error: Throwable?): Boolean {
        return isAmbiguousDmbtFallbackMessage(error?.message)
    }

    private fun DeviceLog.withResolvedDmbtSourceSheetId(): DeviceLog {
        if (sourceSheetId != null) return this
        val resolvedSourceSheetId = sheetConfig.dmbtSheetIdForDiscoveryDate(ngayPhatHien) ?: return this
        return copy(sourceSheetId = resolvedSourceSheetId)
    }

    override suspend fun refreshFromRemote(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "refreshFromRemote start")

            // Bước 1: Pull DMBT logs
            val dmbtResult = try {
                withTimeout(REMOTE_PULL_TIMEOUT_MS) { remoteDataSource.pullLatestLogs() }
            } catch (_: TimeoutCancellationException) {
                Result.failure(
                    IOException(
                        "Hết thời gian chờ kéo dữ liệu DMBT từ Google Sheet sau ${REMOTE_PULL_TIMEOUT_MS / 1000}s."
                    )
                )
            }

            // Nếu DMBT pull thất bại, không chạy repair merge và trả về failure
            if (dmbtResult.isFailure) {
                Log.e(TAG, "refreshFromRemote: DMBT pull failed, skipping repair merge")
                return@withContext Result.failure(dmbtResult.exceptionOrNull() ?: IOException("DMBT pull failed"))
            }

            // Merge DMBT logs vào local
            val logs = dmbtResult.getOrNull() ?: emptyList()
            var appliedCount = 0
            var skippedCount = 0
            val entitiesToUpsert = mutableListOf<DeviceLogEntity>()
            val remotePresentLocalIdsBySheet = linkedMapOf<Int, MutableSet<String>>()
            logs.forEach { remoteLog ->
                val local = resolveExistingLocalForRemote(
                    dao = deviceLogDao,
                    remoteLog = remoteLog
                )
                remoteLog.sourceSheetId?.let { sheetId ->
                    val representativeLocalId = local?.recordId ?: remoteLog.recordId
                    remotePresentLocalIdsBySheet.getOrPut(sheetId) { linkedSetOf() }.add(representativeLocalId)
                }
                if (shouldApplyRemoteLog(local, remoteLog)) {
                    entitiesToUpsert += buildMergedSyncedEntityFromRemote(
                        currentLocal = local,
                        remoteLog = remoteLog,
                        fallbackNowMillis = System.currentTimeMillis()
                    )
                    appliedCount += 1
                } else {
                    skippedCount += 1
                }
            }
            if (entitiesToUpsert.isNotEmpty()) {
                deviceLogDao.upsertAll(entitiesToUpsert)
            }
            Log.i(TAG, "refreshFromRemote: DMBT fetched=${logs.size}, applied=$appliedCount, skipped=$skippedCount")
            val mirrorDeleteStats = mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
                deviceLogDao = deviceLogDao,
                remotePresentLocalIdsBySheet = remotePresentLocalIdsBySheet
            )
            mirrorDeleteStats.forEach { stat ->
                Log.i(
                    TAG,
                    "refreshFromRemote mirrorDelete sourceSheetId=${stat.sourceSheetId} remoteIds=${stat.remoteIdsCount} localSynced=${stat.localSyncedCount} deletedStaleSynced=${stat.deletedStaleCount} keptPendingOrFailed=${stat.keptPendingOrFailedCount}"
                )
            }

            // Bước 2: Merge repair logs từ sheet Sửa chữa
            val repairResult = mergeRepairLogsFromRemote()
            if (shouldFailRefreshAfterRepairFailure(repairResult, repairIsOptional = true)) {
                Log.e(TAG, "refreshFromRemote: repair merge failed and is configured as mandatory")
                return@withContext Result.failure(
                    repairResult.exceptionOrNull() ?: IOException("Repair merge failed")
                )
            }
            if (repairResult.isFailure) {
                // Phase 1 monthly isolation:
                // repair monthly lỗi/missing không được làm fail full sync yearly.
                Log.w(
                    TAG,
                    "refreshFromRemote: repair merge failed but yearly DMBT already synced; continue with warning: ${repairResult.exceptionOrNull()?.message}"
                )
                return@withContext Result.success(Unit)
            }

            Log.i(TAG, "refreshFromRemote complete: success")
            Result.success(Unit)
        }
    }

    suspend fun ignoreAmbiguousPendingRecord(recordId: String): Boolean {
        return ignoreAmbiguousPendingRecord(
            recordId = recordId,
            deviceLogDao = deviceLogDao,
            syncQueueDao = syncQueueDao
        )
    }

    internal fun getLatestDmbtSheetIssueReports(): List<SheetsRemoteDataSource.DmbtSheetIssueReport> {
        return remoteDataSource.getLatestDmbtSheetIssueReports()
    }

    /**
     * Merge repair logs từ sheet DMBT_REPAIR_LOG vào local DMBT records.
     *
     * Rules:
     * - Dùng RepairRecordIdentityResolver để resolve repair recordId sang local recordId
     * - Chỉ merge khi resolve được đúng 1 local recordId
     * - Không tạo bản ghi mới từ repair log
     * - Không ghi đè bản ghi đang PENDING hoặc FAILED
     * - Chỉ cập nhật các trường: ngaySuaChua, ghiChu, updatedAt, syncStatus
     */
    private suspend fun mergeRepairLogsFromRemote(): Result<Unit> {
        return try {
            val repairLogsResult = remoteDataSource.pullRepairLogs(optional = true)
            if (repairLogsResult.isFailure) {
                return Result.failure(repairLogsResult.exceptionOrNull() ?: IOException("Unknown repair pull error"))
            }

            val repairLogs = repairLogsResult.getOrNull() ?: emptyList()
            if (repairLogs.isEmpty()) {
                Log.d(TAG, "mergeRepairLogsFromRemote: no repair logs to merge")
                return Result.success(Unit)
            }

            // Phase 2 partition: repair monthly chỉ được merge vào monthly DMBT candidates.
            val monthlySheetId = SheetConfig.MONTHLY_DMBT_SHEET_IDS.first()
            val localRecordIds = deviceLogDao.getRecordIdsBySourceSheetId(monthlySheetId)
            val monthlyCandidates = localRecordIds.mapNotNull { recordId -> deviceLogDao.getById(recordId) }
            Log.d(
                TAG,
                "mergeRepairLogsFromRemote: monthlyLocalRecordIds count=${localRecordIds.size}, monthlyCandidates=${monthlyCandidates.size}, monthlySheetId=$monthlySheetId"
            )

            if (monthlyCandidates.isEmpty()) {
                Log.w(TAG, "mergeRepairLogsFromRemote: skip all repair logs because no monthly candidates were found")
                return Result.success(Unit)
            }

            var mergedCount = 0
            var skippedNotFound = 0
            var skippedAmbiguous = 0
            var skippedPending = 0

            repairLogs.forEach { repairLog ->
                val matchedLocalRecordId = resolveMonthlyRepairTargetRecordId(
                    repairLog = repairLog,
                    monthlyCandidates = monthlyCandidates
                )

                if (matchedLocalRecordId == null) {
                    // Không resolve được - có thể not found hoặc ambiguous
                    val businessKey = repairLog.toBusinessKeyOrNull()
                    if (businessKey != null) {
                        val matchingCount = monthlyCandidates.count { candidate ->
                            candidate.toBusinessKey() == businessKey
                        }
                        if (matchingCount == 0) {
                            skippedNotFound += 1
                            Log.d(TAG, "mergeRepairLogsFromRemote: skip repair, not found businessKey=$businessKey")
                        } else {
                            skippedAmbiguous += 1
                            Log.d(TAG, "mergeRepairLogsFromRemote: skip repair, ambiguous businessKey=$businessKey ($matchingCount matches)")
                        }
                    } else if (repairLog.recordId.isNotBlank()) {
                        val baseId = RepairRecordIdentityResolver.stripDmbtNamespace(repairLog.recordId)
                        val matchingCount = localRecordIds.count { localId ->
                            RepairRecordIdentityResolver.stripDmbtNamespace(localId) == baseId
                        }
                        if (matchingCount == 0) {
                            skippedNotFound += 1
                            Log.d(TAG, "mergeRepairLogsFromRemote: skip repair, not found recordId=${repairLog.recordId}")
                        } else {
                            skippedAmbiguous += 1
                            Log.d(TAG, "mergeRepairLogsFromRemote: skip repair, ambiguous baseId=$baseId (${matchingCount} matches)")
                        }
                    } else {
                        skippedNotFound += 1
                        Log.d(TAG, "mergeRepairLogsFromRemote: skip repair, no record_id and incomplete business key")
                    }
                    return@forEach
                }

                // Lấy local record sau khi resolve
                val localRecord = deviceLogDao.getById(matchedLocalRecordId)
                if (localRecord == null) {
                    // Không tìm thấy bản ghi local (edge case)
                    skippedNotFound += 1
                    Log.w(TAG, "mergeRepairLogsFromRemote: skip repair, resolved but not found localRecordId=$matchedLocalRecordId")
                    return@forEach
                }

                if (!shouldMergeRepairIntoLocal(localRecord, repairLog)) {
                    // Không merge được (đang PENDING/FAILED hoặc remote cũ hơn)
                    skippedPending += 1
                    Log.d(TAG, "mergeRepairLogsFromRemote: skip repair for recordId=${repairLog.recordId}, localStatus=${localRecord.syncStatus}")
                    return@forEach
                }

                // Merge: cập nhật các trường sửa chữa
                val updatedEntity = localRecord.copy(
                    ngaySuaChua = repairLog.ngaySuaChua,
                    ghiChu = repairLog.ghiChu,
                    updatedAt = if (repairLog.updatedAt > 0L) repairLog.updatedAt else localRecord.updatedAt,
                    syncStatus = "SYNCED"
                )
                deviceLogDao.upsert(updatedEntity)
                mergedCount += 1
                Log.i(TAG, "mergeRepairLogsFromRemote: merged repairRecordId=${repairLog.recordId} -> localRecordId=$matchedLocalRecordId")
            }

            Log.i(TAG, "mergeRepairLogsFromRemote complete: merged=$mergedCount, skippedNotFound=$skippedNotFound, skippedAmbiguous=$skippedAmbiguous, skippedPending=$skippedPending")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "mergeRepairLogsFromRemote error: ${e.message}")
            Result.failure(e)
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
        val queueErrorsByRecordId = queueItems
            .asSequence()
            .filter { it.operation == OP_UPSERT_LOG }
            .mapNotNull { item ->
                val message = item.lastError?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                item.recordId to message
            }
            .toMap()

        val localLogItems = deviceLogDao.getPendingLogs().map { entity ->
            PendingSyncItem(
                id = "log:${entity.recordId}",
                deviceCode = entity.maThietBi.ifBlank { entity.recordId },
                typeLabel = "DMBT",
                detail = queueErrorsByRecordId[entity.recordId] ?: "Chờ đẩy thay đổi lên Google Sheet",
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

internal fun buildMergedSyncedEntityFromRemote(
    currentLocal: DeviceLogEntity?,
    remoteLog: DeviceLog,
    fallbackNowMillis: Long
): DeviceLogEntity {
    val targetRecordId = currentLocal?.recordId ?: remoteLog.recordId
    val targetSourceSheetId = currentLocal?.sourceSheetId ?: remoteLog.sourceSheetId
    val targetUpdatedAt = when {
        remoteLog.updatedAt > 0L -> remoteLog.updatedAt
        currentLocal != null -> currentLocal.updatedAt
        else -> fallbackNowMillis
    }
    return remoteLog.copy(
        recordId = targetRecordId,
        sourceSheetId = targetSourceSheetId,
        updatedAt = targetUpdatedAt
    ).toEntity(syncStatus = "SYNCED")
}

internal fun shouldApplyRemoteLog(currentLocal: DeviceLogEntity?, remoteLog: DeviceLog): Boolean {
    if (currentLocal == null) return true
    if (currentLocal.syncStatus != "SYNCED") return false
    if (shouldBackfillSourceSheetIdOnly(currentLocal, remoteLog)) return true
    if (remoteLog.updatedAt <= 0L) return hasDifferentDmbtContent(currentLocal, remoteLog)
    return remoteLog.updatedAt >= currentLocal.updatedAt
}

internal fun shouldBackfillSourceSheetIdOnly(
    currentLocal: DeviceLogEntity,
    remoteLog: DeviceLog
): Boolean {
    return currentLocal.sourceSheetId == null &&
        remoteLog.sourceSheetId != null &&
        !hasDifferentDmbtContent(currentLocal, remoteLog)
}

internal fun shouldFailRefreshAfterRepairFailure(
    repairResult: Result<Unit>,
    repairIsOptional: Boolean
): Boolean {
    return repairResult.isFailure && !repairIsOptional
}

internal suspend fun resolveExistingLocalForRemote(
    dao: DeviceLogDao,
    remoteLog: DeviceLog
): DeviceLogEntity? {
    dao.getById(remoteLog.recordId)?.let { exact ->
        if (isLocalSourceCompatibleWithRemote(localSourceSheetId = exact.sourceSheetId, remoteSourceSheetId = remoteLog.sourceSheetId)) {
            return exact
        }
    }

    val remoteBusinessKey = remoteLog.toBusinessKey()

    remoteLog.sourceSheetId?.let { sourceSheetId ->
        val sourceCandidates = dao.getBySourceSheetAndDeviceCode(
            sourceSheetId = sourceSheetId,
            deviceCode = remoteLog.maThietBi
        )
        findUniqueBusinessKeyMatch(
            candidates = sourceCandidates,
            expectedBusinessKey = remoteBusinessKey,
            remoteSourceSheetId = sourceSheetId
        )?.let { return it }
    }

    val deviceCandidates = dao.getByDeviceCode(remoteLog.maThietBi)
    val fallbackCandidates = if (remoteLog.sourceSheetId != null) {
        deviceCandidates.filter { candidate -> candidate.sourceSheetId == null }
    } else {
        deviceCandidates
    }
    return findUniqueBusinessKeyMatch(
        candidates = fallbackCandidates,
        expectedBusinessKey = remoteBusinessKey,
        remoteSourceSheetId = remoteLog.sourceSheetId
    )
}

internal fun isLocalSourceCompatibleWithRemote(
    localSourceSheetId: Int?,
    remoteSourceSheetId: Int?
): Boolean {
    if (remoteSourceSheetId == null) return true
    if (localSourceSheetId == null) return true
    return localSourceSheetId == remoteSourceSheetId
}

internal fun findUniqueBusinessKeyMatch(
    candidates: List<DeviceLogEntity>,
    expectedBusinessKey: String,
    remoteSourceSheetId: Int? = null
): DeviceLogEntity? {
    val matched = candidates.filter { candidate ->
        if (candidate.toBusinessKey() != expectedBusinessKey) return@filter false
        if (remoteSourceSheetId == null) return@filter true
        candidate.sourceSheetId == null || candidate.sourceSheetId == remoteSourceSheetId
    }
    return if (matched.size == 1) matched.first() else null
}

internal fun DeviceLog.toBusinessKey(): String {
    return buildDmbtBusinessKey(
        maThietBi = maThietBi,
        ngayPhatHien = ngayPhatHien,
        hangMuc = hangMuc,
        tinhTrangThietBi = tinhTrangThietBi
    )
}

internal fun DeviceLogEntity.toBusinessKey(): String {
    return buildDmbtBusinessKey(
        maThietBi = maThietBi,
        ngayPhatHien = ngayPhatHien,
        hangMuc = hangMuc,
        tinhTrangThietBi = tinhTrangThietBi
    )
}

internal fun buildDmbtBusinessKey(
    maThietBi: String,
    ngayPhatHien: String,
    hangMuc: String,
    tinhTrangThietBi: String
): String {
    val normalizedDate = DateTextFormatter.formatForDisplay(ngayPhatHien)
    return listOf(
        normalizeDmbtKeyPart(maThietBi),
        normalizeDmbtKeyPart(normalizedDate),
        normalizeDmbtKeyPart(hangMuc),
        normalizeDmbtKeyPart(tinhTrangThietBi)
    ).joinToString("|")
}

internal fun DmbtRepairUpdate.toBusinessKeyOrNull(): String? {
    val discovery = ngayPhatHien?.trim().orEmpty()
    val category = hangMuc?.trim().orEmpty()
    val condition = tinhTrangThietBi?.trim().orEmpty()
    if (maThietBi.trim().isBlank() || discovery.isBlank() || category.isBlank() || condition.isBlank()) {
        return null
    }
    return buildDmbtBusinessKey(
        maThietBi = maThietBi,
        ngayPhatHien = discovery,
        hangMuc = category,
        tinhTrangThietBi = condition
    )
}

private data class PushLogsOutcome(
    val successfulLogs: List<DeviceLog> = emptyList(),
    val failuresByRecordId: Map<String, String> = emptyMap(),
    val batchError: Throwable? = null
)

internal suspend fun ignoreAmbiguousPendingRecord(
    recordId: String,
    deviceLogDao: DeviceLogDao,
    syncQueueDao: SyncQueueDao
): Boolean {
    val deleted = syncQueueDao.deleteAmbiguousPushErrorByRecordId(recordId)
    if (deleted <= 0) {
        return false
    }

    val local = deviceLogDao.getById(recordId)
    if (local != null && local.syncStatus == "PENDING") {
        deviceLogDao.upsert(local.copy(syncStatus = "FAILED"))
    }
    return true
}

internal fun isAmbiguousDmbtFallbackMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("Ambiguous DMBT fallback key for push")
}

internal suspend fun mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
    deviceLogDao: DeviceLogDao,
    remoteLogs: List<DeviceLog>
): List<MirrorDeleteSheetStat> {
    val remotePresentLocalIdsBySheet = remoteLogs
        .asSequence()
        .mapNotNull { log ->
            val sheetId = log.sourceSheetId ?: return@mapNotNull null
            sheetId to log.recordId
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, ids) -> ids.toSet() }
    return mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
        deviceLogDao = deviceLogDao,
        remotePresentLocalIdsBySheet = remotePresentLocalIdsBySheet
    )
}

internal suspend fun mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
    deviceLogDao: DeviceLogDao,
    remotePresentLocalIdsBySheet: Map<Int, Set<String>>
): List<MirrorDeleteSheetStat> {
    val stats = mutableListOf<MirrorDeleteSheetStat>()
    remotePresentLocalIdsBySheet.forEach { (sourceSheetId, remoteLocalIds) ->
        val remoteRecordIds = remoteLocalIds.toSet()
        if (remoteRecordIds.isEmpty()) {
            stats += MirrorDeleteSheetStat(
                sourceSheetId = sourceSheetId,
                remoteIdsCount = 0,
                localSyncedCount = 0,
                deletedStaleCount = 0,
                keptPendingOrFailedCount = 0
            )
            return@forEach
        }

        val localRecordIds = deviceLogDao.getRecordIdsBySourceSheetId(sourceSheetId).toSet()
        val localSyncedIds = deviceLogDao.getSyncedRecordIdsBySourceSheetId(sourceSheetId).toSet()
        val staleLocalIds = localRecordIds - remoteRecordIds
        val staleSyncedIds = localSyncedIds.filter { it in staleLocalIds }
        val keptPendingOrFailedCount = staleLocalIds.size - staleSyncedIds.size

        var deletedCount = 0
        if (staleSyncedIds.isNotEmpty()) {
            deletedCount = deviceLogDao.deleteByRecordIds(staleSyncedIds)
        }

        stats += MirrorDeleteSheetStat(
            sourceSheetId = sourceSheetId,
            remoteIdsCount = remoteRecordIds.size,
            localSyncedCount = localSyncedIds.size,
            deletedStaleCount = deletedCount,
            keptPendingOrFailedCount = keptPendingOrFailedCount
        )
    }
    return stats
}

internal data class MirrorDeleteSheetStat(
    val sourceSheetId: Int,
    val remoteIdsCount: Int,
    val localSyncedCount: Int,
    val deletedStaleCount: Int,
    val keptPendingOrFailedCount: Int
)

internal fun resolveMonthlyRepairTargetRecordId(
    repairLog: DmbtRepairUpdate,
    monthlyCandidates: List<DeviceLogEntity>
): String? {
    if (repairLog.recordId.isNotBlank()) {
        val candidateIds = monthlyCandidates.map { it.recordId }
        return RepairRecordIdentityResolver.resolveRepairRecordId(repairLog.recordId, candidateIds)
    }

    val businessKey = repairLog.toBusinessKeyOrNull() ?: return null
    val matches = monthlyCandidates.filter { it.toBusinessKey() == businessKey }
    return if (matches.size == 1) matches.first().recordId else null
}

private fun normalizeDmbtKeyPart(value: String): String {
    return Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}

/**
 * Kiểm tra xem có nên merge repair log từ remote vào local record hay không.
 *
 * Rules:
 * - Bỏ qua nếu local đang PENDING hoặc FAILED (dữ liệu user nhập offline)
 * - Bỏ qua nếu local mới hơn remote (local đã được sửa sau khi tạo repair log)
 * - Chỉ merge khi local đang SYNCED và remote mới hơn hoặc bằng local
 */
internal fun shouldMergeRepairIntoLocal(local: DeviceLogEntity, repairLog: DmbtRepairUpdate): Boolean {
    // Không ghi đè dữ liệu user đang làm việc offline
    if (local.syncStatus == "PENDING" || local.syncStatus == "FAILED") {
        return false
    }

    if (repairLog.updatedAt <= 0L) {
        return hasDifferentRepairContent(local, repairLog)
    }

    // Chỉ merge khi remote repair mới hơn hoặc bằng local
    // Nếu local mới hơn, có nghĩa user đã sửa sau khi repair log được tạo
    return repairLog.updatedAt >= local.updatedAt
}

private fun hasDifferentDmbtContent(local: DeviceLogEntity, remote: DeviceLog): Boolean {
    return local.maThietBi.trim() != remote.maThietBi.trim() ||
        local.hangMuc.trim() != remote.hangMuc.trim() ||
        local.nguoiBaoCao.trim() != remote.nguoiBaoCao.trim() ||
        local.tinhTrangThietBi.trim() != remote.tinhTrangThietBi.trim() ||
        local.ktvPhuTrach.trim() != remote.ktvPhuTrach.trim() ||
        DateTextFormatter.formatForDisplay(local.ngayPhatHien) != DateTextFormatter.formatForDisplay(remote.ngayPhatHien) ||
        DateTextFormatter.formatForDisplay(local.ngaySuaChua.orEmpty()).takeIf { it != "--" }.orEmpty() !=
        DateTextFormatter.formatForDisplay(remote.ngaySuaChua.orEmpty()).takeIf { it != "--" }.orEmpty() ||
        local.ghiChu.trim() != remote.ghiChu.trim()
}

private fun hasDifferentRepairContent(local: DeviceLogEntity, repairLog: DmbtRepairUpdate): Boolean {
    return DateTextFormatter.formatForDisplay(local.ngaySuaChua.orEmpty()).takeIf { it != "--" }.orEmpty() !=
        DateTextFormatter.formatForDisplay(repairLog.ngaySuaChua.orEmpty()).takeIf { it != "--" }.orEmpty() ||
        local.ghiChu.trim() != repairLog.ghiChu.trim()
}
