package com.example.devicetracker.data.bootstrap

import android.content.Context
import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.local.entity.HgtCheckEntity
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.HgtDateCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SeedLocalDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceLogDao: DeviceLogDao,
    private val hgtCheckDao: HgtCheckDao
) {
    suspend fun seedIfDatabaseEmpty(): Int = withContext(Dispatchers.IO) {
        if (!isBundledSnapshotSeedEnabled()) {
            return@withContext 0
        }
        seedDeviceLogs() + seedHgtChecks()
    }

    private suspend fun seedDeviceLogs(): Int {
        val seedRows = readSeedRows()
        if (seedRows.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val entities = seedRows.mapIndexed { index, row ->
            DeviceLogEntity(
                recordId = row.recordId,
                maThietBi = row.maThietBi,
                hangMuc = row.hangMuc,
                nguoiBaoCao = row.nguoiBaoCao,
                tinhTrangThietBi = row.tinhTrangThietBi,
                ktvPhuTrach = row.ktvPhuTrach,
                ngayPhatHien = normalizeSeedDate(row.ngayPhatHien),
                ngaySuaChua = normalizeSeedDate(row.ngaySuaChua).ifBlank { null },
                ghiChu = row.ghiChu,
                updatedAt = now - index,
                syncStatus = "SYNCED"
            )
        }

        if (deviceLogDao.countAll() == 0) {
            deviceLogDao.upsertAll(entities)
            return entities.size
        }

        var changedCount = 0
        entities.forEach { entity ->
            val current = deviceLogDao.getById(entity.recordId)
            if (current?.syncStatus == "PENDING") return@forEach

            deviceLogDao.upsert(entity)
            changedCount += 1
        }
        return changedCount
    }

    private suspend fun seedHgtChecks(): Int {
        val seedRows = readHgtSeedRows()
        if (seedRows.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val entities = seedRows.mapIndexed { index, row ->
            val latestDate = normalizeSeedDate(row.lanGanNhat)
            val nextDate = normalizeSeedDate(row.lanTiepTheo).ifBlank {
                HgtDateCalculator.calculateNextDate(latestDate, row.chuKyNgay)
            }
            HgtCheckEntity(
                id = row.id,
                maThietBi = row.maThietBi,
                chuKyNgay = row.chuKyNgay,
                lanGanNhat = latestDate,
                lanTiepTheo = nextDate,
                ghiChu = row.ghiChu,
                updatedAt = now - index,
                syncStatus = "SYNCED"
            )
        }

        if (hgtCheckDao.countAll() == 0) {
            hgtCheckDao.upsertAll(entities)
            return entities.size
        }

        var changedCount = 0
        entities.forEach { entity ->
            val current = hgtCheckDao.getById(entity.id)
            if (current?.syncStatus == "PENDING") return@forEach

            hgtCheckDao.upsert(entity)
            changedCount += 1
        }
        return changedCount
    }

    private fun readSeedRows(): List<SeedRow> {
        val jsonText = context.assets
            .open(SEED_ASSET_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .trimStart('\uFEFF')
            .trim()

        if (jsonText.isBlank()) return emptyList()

        val array = JSONArray(jsonText)
        val rows = mutableListOf<SeedRow>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val row = item.toSeedRow() ?: continue
            if (row.maThietBi.isBlank()) continue
            rows += row
        }
        return rows
    }

    private fun readHgtSeedRows(): List<HgtSeedRow> {
        val jsonText = runCatching {
            context.assets
                .open(HGT_SEED_ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .trimStart('\uFEFF')
                .trim()
        }.getOrDefault("")

        if (jsonText.isBlank()) return emptyList()

        val array = JSONArray(jsonText)
        val rows = mutableListOf<HgtSeedRow>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val row = item.toHgtSeedRow() ?: continue
            if (row.maThietBi.isBlank() || row.chuKyNgay <= 0) continue
            rows += row
        }
        return rows
    }

    private fun JSONObject.toSeedRow(): SeedRow? {
        val recordId = optString("recordId").trim()
        if (recordId.isBlank()) return null

        return SeedRow(
            recordId = recordId,
            maThietBi = optString("maThietBi").trim(),
            hangMuc = optString("hangMuc").trim(),
            nguoiBaoCao = optString("nguoiBaoCao").trim(),
            tinhTrangThietBi = optString("tinhTrangThietBi").trim(),
            ktvPhuTrach = optString("ktvPhuTrach").trim(),
            ngayPhatHien = optString("ngayPhatHien").trim(),
            ngaySuaChua = optString("ngaySuaChua").trim(),
            ghiChu = optString("ghiChu").trim()
        )
    }

    private fun JSONObject.toHgtSeedRow(): HgtSeedRow? {
        val id = optString("id").trim()
        if (id.isBlank()) return null

        return HgtSeedRow(
            id = id,
            maThietBi = optString("maThietBi").trim(),
            chuKyNgay = optInt("chuKyNgay", 0),
            lanGanNhat = optString("lanGanNhat").trim(),
            lanTiepTheo = optString("lanTiepTheo").trim(),
            ghiChu = optString("ghiChu").trim()
        )
    }

    private fun normalizeSeedDate(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return ""

        DateTextFormatter.normalizeInputOrNull(trimmed)?.let { return it }

        val monthFirst = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(trimmed)
        if (monthFirst != null) {
            val first = monthFirst.groupValues[1]
            val second = monthFirst.groupValues[2]
            val year = monthFirst.groupValues[3]
            if (first.length == 1 || second.length == 1 || first.toIntOrNull() ?: 0 > 12) {
                return DateTextFormatter.normalizeInputOrNull(
                    "${second.padStart(2, '0')}/${first.padStart(2, '0')}/$year"
                ) ?: trimmed
            }
        }

        return DateTextFormatter.formatForDisplay(trimmed)
    }

    private data class SeedRow(
        val recordId: String,
        val maThietBi: String,
        val hangMuc: String,
        val nguoiBaoCao: String,
        val tinhTrangThietBi: String,
        val ktvPhuTrach: String,
        val ngayPhatHien: String,
        val ngaySuaChua: String,
        val ghiChu: String
    )

    private data class HgtSeedRow(
        val id: String,
        val maThietBi: String,
        val chuKyNgay: Int,
        val lanGanNhat: String,
        val lanTiepTheo: String,
        val ghiChu: String
    )

    companion object {
        private const val SEED_ASSET_FILE = "seed_device_logs.json"
        private const val HGT_SEED_ASSET_FILE = "seed_hgt_checks.json"
        internal fun isBundledSnapshotSeedEnabled(): Boolean = false
    }
}
