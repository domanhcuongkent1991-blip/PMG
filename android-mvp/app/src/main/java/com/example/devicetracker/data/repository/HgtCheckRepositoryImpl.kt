package com.example.devicetracker.data.repository

import android.util.Log
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.local.entity.HgtCheckEntity
import com.example.devicetracker.data.local.entity.SyncQueueEntity
import com.example.devicetracker.data.remote.SheetsRemoteDataSource
import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.domain.repository.HgtCheckRepository
import com.example.devicetracker.util.HgtDateCalculator
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

@Singleton
class HgtCheckRepositoryImpl @Inject constructor(
    private val hgtCheckDao: HgtCheckDao,
    private val syncQueueDao: SyncQueueDao,
    private val remoteDataSource: SheetsRemoteDataSource
) : HgtCheckRepository {

    override fun observeChecks(query: String): Flow<List<HgtCheck>> {
        return hgtCheckDao.observeByDeviceCode(query.trim())
            .map { items -> items.map { it.toDomain() } }
    }

    override suspend fun updateLatestCheckDate(id: String, latestDate: String) {
        val current = hgtCheckDao.getById(id) ?: return
        upsertCheck(
            id = current.id,
            maThietBi = current.maThietBi,
            chuKyNgay = current.chuKyNgay,
            lanGanNhat = latestDate
        )
    }

    override suspend fun upsertCheck(
        id: String?,
        maThietBi: String,
        chuKyNgay: Int,
        lanGanNhat: String
    ) {
        require(maThietBi.isNotBlank()) { "Ma thiet bi khong duoc de trong" }
        require(chuKyNgay > 0) { "Chu ky phai lon hon 0" }
        require(lanGanNhat.isNotBlank()) { "Lan kiem tra gan nhat khong duoc de trong" }

        val normalizedDeviceCode = maThietBi.trim().uppercase(Locale.ROOT)
        val nextDate = HgtDateCalculator.calculateNextDate(lanGanNhat, chuKyNgay)
        val resolvedId = id?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "hgt-local-${normalizedDeviceCode.lowercase(Locale.ROOT)}-${System.currentTimeMillis()}"

        val current = hgtCheckDao.getById(resolvedId)
        if (id != null && current == null) {
            throw IllegalArgumentException("Khong tim thay ban ghi HGT de cap nhat")
        }

        if (current != null && current.maThietBi != normalizedDeviceCode) {
            enqueueDeleteByDeviceCode(current.maThietBi)
        }

        val entity = HgtCheckEntity(
            id = resolvedId,
            maThietBi = normalizedDeviceCode,
            chuKyNgay = chuKyNgay,
            lanGanNhat = lanGanNhat,
            lanTiepTheo = nextDate,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )

        hgtCheckDao.upsert(entity)
        enqueueUpsert(entity.id)
        Log.i(TAG, "upsertCheck queued id=${entity.id}")
    }

    override suspend fun deleteCheck(id: String) {
        val current = hgtCheckDao.getById(id) ?: return
        hgtCheckDao.deleteById(id)
        enqueueDeleteByDeviceCode(current.maThietBi)
        syncQueueDao.deleteByRecordId(toUpsertQueueKey(id))
        Log.i(TAG, "deleteCheck queued deviceCode=${current.maThietBi}")
    }

    override suspend fun syncPending(): Result<Unit> {
        val queueSnapshot = syncQueueDao.getAll()
            .filter { it.operation == OP_UPSERT_HGT || it.operation == OP_DELETE_HGT }
        if (queueSnapshot.isEmpty()) {
            Log.d(TAG, "syncPending skipped: HGT queue is empty")
            return Result.success(Unit)
        }

        val upsertItems = queueSnapshot
            .filter { it.operation == OP_UPSERT_HGT }
            .mapNotNull { item ->
                val checkId = fromUpsertQueueKey(item.recordId) ?: return@mapNotNull null
                hgtCheckDao.getById(checkId)?.toDomain()
            }
            .distinctBy { it.id }

        val deleteDeviceCodes = queueSnapshot
            .filter { it.operation == OP_DELETE_HGT }
            .mapNotNull { fromDeleteQueueKey(it.recordId) }
            .distinct()

        val result = remoteDataSource.pushHgtChecks(
            upsertedChecks = upsertItems,
            deletedDeviceCodes = deleteDeviceCodes
        )

        if (result.isSuccess) {
            upsertItems.forEach { pushed ->
                val current = hgtCheckDao.getById(pushed.id)
                if (shouldMarkHgtAsSynced(current, pushed)) {
                    val currentCheck = current ?: return@forEach
                    hgtCheckDao.upsert(currentCheck.copy(syncStatus = "SYNCED"))
                }
            }
            queueSnapshot.forEach { syncQueueDao.deleteById(it.id) }
            Log.i(
                TAG,
                "syncPending success: queueDeleted=${queueSnapshot.size}, upserts=${upsertItems.size}, deletes=${deleteDeviceCodes.size}"
            )
            return Result.success(Unit)
        }

        val message = result.exceptionOrNull()?.message ?: "Unknown HGT sync error"
        queueSnapshot.forEach { syncQueueDao.markFailed(it.id, message) }
        Log.e(TAG, "syncPending failed: queueMarkedFailed=${queueSnapshot.size}, error=$message")
        return result
    }

    override suspend fun refreshFromRemote(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "refreshFromRemote start")
            val result = try {
                withTimeout(REMOTE_PULL_TIMEOUT_MS) { remoteDataSource.pullHgtChecks() }
            } catch (_: TimeoutCancellationException) {
                Result.failure(
                    IOException(
                        "Hết thời gian chờ kéo dữ liệu HGT từ Google Sheet sau ${REMOTE_PULL_TIMEOUT_MS / 1000}s."
                    )
                )
            }
            result.onSuccess { remoteChecks ->
                var appliedCount = 0
                var skippedCount = 0
                val localChecks = hgtCheckDao.getAll()
                val localByDeviceCode = localChecks.associateBy { normalizeHgtDeviceCodeKey(it.maThietBi) }
                val entitiesToUpsert = mutableListOf<HgtCheckEntity>()
                remoteChecks.forEach { remote ->
                    val local = hgtCheckDao.getById(remote.id)
                        ?: localByDeviceCode[normalizeHgtDeviceCodeKey(remote.maThietBi)]
                    if (shouldApplyRemoteHgt(local, remote)) {
                        entitiesToUpsert += HgtCheckEntity(
                            id = local?.id ?: remote.id,
                            maThietBi = remote.maThietBi,
                            chuKyNgay = remote.chuKyNgay,
                            lanGanNhat = remote.lanGanNhat,
                            lanTiepTheo = remote.lanTiepTheo,
                            updatedAt = if (remote.updatedAt > 0) remote.updatedAt else System.currentTimeMillis(),
                            syncStatus = "SYNCED"
                        )
                        appliedCount += 1
                    } else {
                        skippedCount += 1
                    }
                }
                if (entitiesToUpsert.isNotEmpty()) {
                    hgtCheckDao.upsertAll(entitiesToUpsert)
                }
                Log.i(TAG, "refreshFromRemote success: fetched=${remoteChecks.size}, applied=$appliedCount, skipped=$skippedCount")
            }.onFailure { throwable ->
                Log.e(TAG, "refreshFromRemote failed: ${throwable.message}")
            }
            result.map { Unit }
        }
    }

    private suspend fun enqueueUpsert(checkId: String) {
        syncQueueDao.deleteByRecordId(toUpsertQueueKey(checkId))
        syncQueueDao.insert(
            SyncQueueEntity(
                recordId = toUpsertQueueKey(checkId),
                operation = OP_UPSERT_HGT,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun enqueueDeleteByDeviceCode(deviceCode: String) {
        val queueKey = toDeleteQueueKey(deviceCode)
        syncQueueDao.deleteByRecordId(queueKey)
        syncQueueDao.insert(
            SyncQueueEntity(
                recordId = queueKey,
                operation = OP_DELETE_HGT,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun HgtCheckEntity.toDomain(): HgtCheck =
        HgtCheck(
            id = id,
            maThietBi = maThietBi,
            chuKyNgay = chuKyNgay,
            lanGanNhat = lanGanNhat,
            lanTiepTheo = lanTiepTheo,
            updatedAt = updatedAt
        )

    private fun toUpsertQueueKey(id: String): String = "$QUEUE_KEY_UPSERT_PREFIX$id"

    private fun fromUpsertQueueKey(queueKey: String): String? =
        queueKey.removePrefix(QUEUE_KEY_UPSERT_PREFIX).takeIf { queueKey.startsWith(QUEUE_KEY_UPSERT_PREFIX) && it.isNotBlank() }

    private fun toDeleteQueueKey(deviceCode: String): String =
        "$QUEUE_KEY_DELETE_PREFIX${deviceCode.trim().lowercase(Locale.ROOT)}"

    private fun fromDeleteQueueKey(queueKey: String): String? =
        queueKey.removePrefix(QUEUE_KEY_DELETE_PREFIX).takeIf { queueKey.startsWith(QUEUE_KEY_DELETE_PREFIX) && it.isNotBlank() }

    companion object {
        private const val TAG = "HgtCheckRepository"
        private const val REMOTE_PULL_TIMEOUT_MS = 180_000L
        private const val OP_UPSERT_HGT = "UPSERT_HGT"
        private const val OP_DELETE_HGT = "DELETE_HGT"
        private const val QUEUE_KEY_UPSERT_PREFIX = "hgt-upsert:"
        private const val QUEUE_KEY_DELETE_PREFIX = "hgt-delete:"
    }
}

internal fun shouldMarkHgtAsSynced(currentLocal: HgtCheckEntity?, pushed: HgtCheck): Boolean {
    if (currentLocal == null) return false
    return currentLocal.updatedAt == pushed.updatedAt
}

internal fun shouldApplyRemoteHgt(currentLocal: HgtCheckEntity?, remote: HgtCheck): Boolean {
    if (currentLocal == null) return true
    if (currentLocal.syncStatus != "SYNCED") return false
    if (remote.updatedAt <= 0L) return hasDifferentHgtContent(currentLocal, remote)
    return remote.updatedAt >= currentLocal.updatedAt
}

private fun hasDifferentHgtContent(currentLocal: HgtCheckEntity, remote: HgtCheck): Boolean {
    return currentLocal.maThietBi != remote.maThietBi ||
        currentLocal.chuKyNgay != remote.chuKyNgay ||
        currentLocal.lanGanNhat != remote.lanGanNhat ||
        currentLocal.lanTiepTheo != remote.lanTiepTheo
}

private fun normalizeHgtDeviceCodeKey(value: String): String =
    value.trim().lowercase(Locale.ROOT)
