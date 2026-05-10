package com.example.devicetracker.data.remote

import android.util.Log
import com.example.devicetracker.data.model.DmbtRepairUpdate
import com.example.devicetracker.data.model.toDmbtLogRow
import com.example.devicetracker.data.model.toHgtRow
import com.example.devicetracker.data.sheet.DmbtLogColumns
import com.example.devicetracker.data.sheet.DmbtRepairLogColumns
import com.example.devicetracker.data.sheet.HgtCheckColumns
import com.example.devicetracker.data.sheet.SheetConfig
import com.example.devicetracker.data.sheet.SheetContract
import com.example.devicetracker.data.sheet.SheetRole
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.HgtDateCalculator
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Sheets remote data source.
 * Uses sheetId-based role mapping to avoid dependency on tab name/order.
 */
@Singleton
class SheetsRemoteDataSource @Inject constructor(
    private val sheetConfig: SheetConfig
) {
    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedAccessTokenExpiresAt: Long = 0L

    @Volatile
    private var latestDmbtSheetIssueReports: List<DmbtSheetIssueReport> = emptyList()

    internal fun getLatestDmbtSheetIssueReports(): List<DmbtSheetIssueReport> = latestDmbtSheetIssueReports

    suspend fun pushLogs(logs: List<DeviceLog>): Result<Unit> {
        val validationResult = validateStructure()
        if (validationResult.isFailure) return validationResult
        if (logs.isEmpty()) return Result.success(Unit)
        val provenanceConfigResult = validateDmbtProvenanceConfig(
            bindings = sheetConfig.dmbtSheetBindings,
            yearlySheetIds = sheetConfig.yearlyDmbtSheetBindings.map { it.sheetId }.toSet()
        )
        if (provenanceConfigResult.isFailure) return provenanceConfigResult

        val payloadValidation = validateDmbtLogPayload(logs)
        if (payloadValidation.isFailure) return payloadValidation

        return runCatching {
            withContext(Dispatchers.IO) {
                val accessToken = requireAccessToken()
                val defaultCreateSheetId = sheetConfig.dmbtDefaultCreateSheetId
                    ?: sheetConfig.sheetId(SheetRole.DMBT_LOG)
                val logsByTargetSheet = groupDmbtLogsByTargetSheet(
                    logs = logs,
                    defaultCreateSheetId = defaultCreateSheetId,
                    configuredDmbtSheetIds = sheetConfig.dmbtSheetBindings.map { it.sheetId }.toSet()
                )
                if (logsByTargetSheet.values.sumOf { it.size } != logs.size) {
                    throw NonRetryableSyncException(
                        "DMBT_LOG payload has unresolved target sheet. Add sourceSheetId for legacy rows before push."
                    )
                }

                val metadata = fetchSpreadsheetMetadata(accessToken)
                logsByTargetSheet.forEach { (targetSheetId, targetLogs) ->
                    pushLogsToDmbtSheet(
                        targetSheetId = targetSheetId,
                        logs = targetLogs,
                        metadata = metadata,
                        accessToken = accessToken
                    )
                }
            }
        }
    }

    private fun pushLogsToDmbtSheet(
        targetSheetId: Int,
        logs: List<DeviceLog>,
        metadata: SpreadsheetMetadata,
        accessToken: String
    ) {
        val dmbtSheetTitle = metadata.sheetTitleById[targetSheetId]
            ?: throw NonRetryableSyncException("Cannot resolve title for DMBT_LOG sheetId=$targetSheetId.")

        val gridRows = fetchGridRows(sheetTitle = dmbtSheetTitle, accessToken = accessToken)
        if (gridRows.isEmpty()) {
            throw NonRetryableSyncException("DMBT_LOG sheetId=$targetSheetId has no header row.")
        }

        val schema = parseDmbtSchema(gridRows)
        val maxColumnLabel = toA1Column(schema.rawHeaders.size)
        val updates = mutableListOf<Pair<Int, List<String>>>()
        val appends = mutableListOf<List<String>>()
        dedupeDmbtLogsForPush(targetSheetId = targetSheetId, logs = logs).forEach { log ->
            val sheetRecordId = resolveDmbtSheetRecordId(
                targetSheetId = targetSheetId,
                recordId = log.recordId
            )
            val sheetLog = log.copy(recordId = sheetRecordId)
            val existingRow = schema.findExistingRow(sheetLog) { maThietBi, ngayPhatHien, hangMuc, tinhTrang ->
                buildDmbtMatchKey(
                    maThietBi = maThietBi,
                    ngayPhatHien = ngayPhatHien,
                    hangMuc = hangMuc,
                    tinhTrangThietBi = tinhTrang
                )
            }
            if (existingRow != null) {
                val currentRow = schema.rowValuesByRowNumber[existingRow].orEmpty()
                val rowValues = schema.mergeRowValues(
                    existingRow = currentRow,
                    rowByContractColumn = sheetLog.toDmbtLogRow()
                )
                updates += existingRow to rowValues
            } else {
                val rowValues = schema.buildAppendRowValues(sheetLog.toDmbtLogRow())
                appends += rowValues
            }
        }
        if (updates.isNotEmpty()) {
            batchUpdateRows(
                sheetTitle = dmbtSheetTitle,
                maxColumnLabel = maxColumnLabel,
                rowUpdates = updates,
                accessToken = accessToken
            )
        }
        if (appends.isNotEmpty()) {
            appendRows(
                sheetTitle = dmbtSheetTitle,
                maxColumnLabel = maxColumnLabel,
                rows = appends,
                accessToken = accessToken
            )
        }
    }

    suspend fun pullLatestLogs(): Result<List<DeviceLog>> {
        val validationResult = validateStructure()
        if (validationResult.isFailure) {
            val validationError = validationResult.exceptionOrNull()
                ?: NonRetryableSyncException("Unknown structure validation error.")
            return Result.failure(validationError)
        }
        val provenanceConfigResult = validateDmbtProvenanceConfig(
            bindings = sheetConfig.dmbtSheetBindings,
            yearlySheetIds = sheetConfig.yearlyDmbtSheetBindings.map { it.sheetId }.toSet()
        )
        if (provenanceConfigResult.isFailure) {
            val error = provenanceConfigResult.exceptionOrNull()
                ?: NonRetryableSyncException("Invalid DMBT provenance mapping.")
            return Result.failure(error)
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                latestDmbtSheetIssueReports = emptyList()
                val accessToken = requireAccessToken()
                val yearlyBindings = sheetConfig.yearlyDmbtSheetBindings
                val yearlyPullTargets = dmbtPullTargets(yearlyBindings)
                if (yearlyPullTargets.isEmpty()) {
                    throw NonRetryableSyncException("Missing DMBT sheet bindings in config.")
                }
                val monthlyPullTargets = dmbtPullTargets(sheetConfig.monthlyDmbtSheetBindings)

                val metadata = fetchSpreadsheetMetadata(accessToken)
                val pulledLogs = mutableListOf<DeviceLog>()
                pullDmbtTargetsWithFaultIsolation(
                    targets = yearlyPullTargets,
                    metadata = metadata,
                    accessToken = accessToken,
                    pulledLogs = pulledLogs,
                    yearlySheetIds = yearlyBindings.map { it.sheetId }.toSet()
                )
                pullDmbtTargetsWithFaultIsolation(
                    targets = monthlyPullTargets,
                    metadata = metadata,
                    accessToken = accessToken,
                    pulledLogs = pulledLogs,
                    yearlySheetIds = yearlyBindings.map { it.sheetId }.toSet()
                )

                pulledLogs
            }
        }
    }

    private fun pullDmbtTargetsWithFaultIsolation(
        targets: List<DmbtPullTarget>,
        metadata: SpreadsheetMetadata,
        accessToken: String,
        pulledLogs: MutableList<DeviceLog>,
        yearlySheetIds: Set<Int>
    ) {
        targets.forEach { target ->
            val sheetTitle = metadata.sheetTitleById[target.sheetId]
            if (sheetTitle == null) {
                val error = NonRetryableSyncException(
                    "Cannot resolve title for configured DMBT sheetId=${target.sheetId}."
                )
                if (isYearlyDmbtSheetFailureFatal(target.sheetId, yearlySheetIds)) {
                    throw error
                }
                Log.w(TAG, "pullLatestLogs skipped optional monthly DMBT sheetId=${target.sheetId}: ${error.message}")
                return@forEach
            }

            try {
                pulledLogs += pullDmbtLogsFromSheet(
                    sheetId = target.sheetId,
                    sheetTitle = sheetTitle,
                    accessToken = accessToken,
                    namespaceRecordIds = target.namespaceRecordIds
                )
            } catch (e: Exception) {
                if (isYearlyDmbtSheetFailureFatal(target.sheetId, yearlySheetIds)) {
                    throw e
                }
                Log.w(
                    TAG,
                    "pullLatestLogs skipped optional monthly DMBT sheetId=${target.sheetId}: ${e.message}"
                )
            }
        }
    }

    private fun pullDmbtLogsFromSheet(
        sheetId: Int,
        sheetTitle: String,
        accessToken: String,
        namespaceRecordIds: Boolean
    ): List<DeviceLog> {
        val gridRows = fetchGridRows(sheetTitle = sheetTitle, accessToken = accessToken)
        if (gridRows.isEmpty()) return emptyList()

        val schema = parseDmbtSchema(gridRows)
        var skippedRows = 0
        val skippedInvalidRowSamples = mutableListOf<Int>()
        val pulledLogRows = schema.rows.mapNotNull { row ->
            val maThietBi = schema.valueFromRow(row.values, DmbtLogColumns.MA_THIET_BI).trim()
            if (maThietBi.isBlank()) return@mapNotNull null

            val hangMuc = schema.valueFromRow(row.values, DmbtLogColumns.HANG_MUC).trim()
            val nguoiBaoCao = schema.valueFromRow(row.values, DmbtLogColumns.NGUOI_BAO_CAO).trim()
            val tinhTrang = schema.valueFromRow(row.values, DmbtLogColumns.TINH_TRANG_THIET_BI).trim()
            val ktvPhuTrach = schema.valueFromRow(row.values, DmbtLogColumns.KTV_PHU_TRACH).trim()
            val ngayPhatHienRaw = schema.valueFromRow(row.values, DmbtLogColumns.NGAY_PHAT_HIEN).trim()
            val ngayPhatHien = DateTextFormatter.normalizeInputOrNull(ngayPhatHienRaw)
                ?: DateTextFormatter.formatForDisplay(ngayPhatHienRaw)
            if (ngayPhatHien.isBlank() || ngayPhatHien == "--") {
                // Keep pull stable: skip malformed/incomplete rows instead of failing all DMBT pull.
                skippedRows += 1
                if (skippedInvalidRowSamples.size < 10) {
                    skippedInvalidRowSamples += row.rowNumber
                }
                Log.w(
                    TAG,
                    "pullLatestLogs skipped DMBT sheetId=$sheetId row=${row.rowNumber}: invalid ngay_phat_hien='$ngayPhatHienRaw'"
                )
                return@mapNotNull null
            }

            val ngaySuaRaw = schema.valueFromRow(row.values, DmbtLogColumns.NGAY_SUA_CHUA).trim()
            val ngaySua = DateTextFormatter.normalizeInputOrNull(ngaySuaRaw)
                ?: DateTextFormatter.formatForDisplay(ngaySuaRaw)
            val ngaySuaChua = ngaySua.takeIf { it.isNotBlank() && it != "--" }
            val ghiChu = schema.valueFromRow(row.values, DmbtLogColumns.GHI_CHU).trim()

            val recordIdRaw = schema.valueFromRow(row.values, DmbtLogColumns.RECORD_ID).trim()
            val baseRecordId = recordIdRaw.ifBlank {
                buildDmbtFallbackRecordId(
                    maThietBi = maThietBi,
                    ngayPhatHien = ngayPhatHien,
                    hangMuc = hangMuc,
                    tinhTrangThietBi = tinhTrang,
                    rowNumber = row.rowNumber
                )
            }
            val recordIdentity = dmbtPulledRecordIdentity(
                sheetId = sheetId,
                baseRecordId = baseRecordId,
                namespaceRecordIds = namespaceRecordIds
            )
            val updatedAtRaw = schema.valueFromRow(row.values, DmbtLogColumns.UPDATED_AT).trim()
            val updatedAt = updatedAtRaw.toLongOrNull() ?: 0L

            DmbtPulledLogRow(
                rowNumber = row.rowNumber,
                log = DeviceLog(
                    recordId = recordIdentity.recordId,
                    maThietBi = maThietBi,
                    hangMuc = hangMuc,
                    nguoiBaoCao = nguoiBaoCao,
                    tinhTrangThietBi = tinhTrang,
                    ktvPhuTrach = ktvPhuTrach,
                    ngayPhatHien = ngayPhatHien,
                    ngaySuaChua = ngaySuaChua,
                    ghiChu = ghiChu,
                    updatedAt = updatedAt,
                    sourceSheetId = recordIdentity.sourceSheetId
                )
            )
        }
        val pulledLogs = pulledLogRows.map { it.log }
        if (skippedRows > 0) {
            Log.w(TAG, "pullLatestLogs skippedRows=$skippedRows for DMBT sheetId=$sheetId title='$sheetTitle'")
        }
        val stats = buildDmbtPullSheetStats(
            sheetId = sheetId,
            sheetTitle = sheetTitle,
            pulledLogs = pulledLogs,
            skippedInvalidRows = skippedRows,
            rowNumbersByRecordId = pulledLogRows.groupBy(
                keySelector = { it.log.recordId },
                valueTransform = { it.rowNumber }
            ),
            skippedInvalidRowSamples = skippedInvalidRowSamples
        )
        val sheetIssueReports = buildDmbtSheetIssueReports(
            sheetTitle = sheetTitle,
            stats = stats,
            pulledLogs = pulledLogs
        )
        if (sheetIssueReports.isNotEmpty()) {
            latestDmbtSheetIssueReports = latestDmbtSheetIssueReports + sheetIssueReports
        }
        Log.i(
            TAG,
            "pullLatestLogs sheetStats DMBT sheetId=${stats.sheetId} title='${stats.sheetTitle}' " +
                "fetchedRows=${stats.fetchedRows} uniqueRemoteIds=${stats.uniqueRemoteIds} " +
                "duplicateRemoteIds=${stats.duplicateRemoteIds} skippedInvalidRows=${stats.skippedInvalidRows} " +
                "skippedInvalidRowSamples=${stats.skippedInvalidRowSamples}"
        )
        if (stats.duplicateRemoteIds > 0) {
            Log.w(
                TAG,
                "pullLatestLogs duplicate remote DMBT identities sheetId=${stats.sheetId} " +
                    "title='${stats.sheetTitle}' duplicateRemoteIds=${stats.duplicateRemoteIds} " +
                    "duplicateRemoteIdSamples=${stats.duplicateRemoteIdSamples.joinToString(limit = 10)} " +
                    "duplicateRemoteRowSamples=${stats.duplicateRemoteRowSamples.joinToString(limit = 10)}"
            )
        }
        return pulledLogs
    }

    suspend fun pushHgtChecks(
        upsertedChecks: List<HgtCheck>,
        deletedDeviceCodes: List<String>
    ): Result<Unit> {
        if (upsertedChecks.isEmpty() && deletedDeviceCodes.isEmpty()) {
            return Result.success(Unit)
        }
        val hgtSheetId = sheetConfig.sheetId(SheetRole.HGT_CHECKS) ?: return Result.success(Unit)

        return runCatching {
            withContext(Dispatchers.IO) {
                val accessToken = requireAccessToken()
                val metadata = fetchSpreadsheetMetadata(accessToken)
                val hgtSheetTitle = metadata.sheetTitleById[hgtSheetId]
                    ?: throw NonRetryableSyncException("Cannot resolve title for HGT_CHECKS sheetId=$hgtSheetId.")

                val gridRows = fetchGridRows(sheetTitle = hgtSheetTitle, accessToken = accessToken)
                if (gridRows.isEmpty()) {
                    throw NonRetryableSyncException("HGT_CHECKS sheet has no header row.")
                }

                val schema = parseHgtSchema(gridRows)
                val maxColumnLabel = toA1Column(schema.rawHeaders.size)
                val updates = mutableListOf<Pair<Int, List<String>>>()
                val appends = mutableListOf<List<String>>()

                upsertedChecks.forEach { check ->
                    val existingRow = schema.findExistingRow(check)
                    if (existingRow != null) {
                        val currentRow = schema.rowValuesByRowNumber[existingRow].orEmpty()
                        val rowValues = schema.mergeRowValues(
                            existingRow = currentRow,
                            rowByContractColumn = check.toHgtRow()
                        )
                        updates += existingRow to rowValues
                    } else {
                        val rowValues = schema.buildAppendRowValues(check.toHgtRow())
                        appends += rowValues
                    }
                }
                if (updates.isNotEmpty()) {
                    batchUpdateRows(
                        sheetTitle = hgtSheetTitle,
                        maxColumnLabel = maxColumnLabel,
                        rowUpdates = updates,
                        accessToken = accessToken
                    )
                }
                if (appends.isNotEmpty()) {
                    appendRows(
                        sheetTitle = hgtSheetTitle,
                        maxColumnLabel = maxColumnLabel,
                        rows = appends,
                        accessToken = accessToken
                    )
                }

                val rowsToDelete = deletedDeviceCodes
                    .map { normalizeDeviceCodeKey(it) }
                    .distinct()
                    .mapNotNull { schema.rowByDeviceCode[it] }
                    .distinct()
                    .sortedDescending()

                if (rowsToDelete.isNotEmpty()) {
                    deleteRows(
                        sheetId = hgtSheetId,
                        rowNumbers = rowsToDelete,
                        accessToken = accessToken
                    )
                }
            }
        }
    }

    suspend fun pullHgtChecks(): Result<List<HgtCheck>> {
        val hgtSheetId = sheetConfig.sheetId(SheetRole.HGT_CHECKS) ?: return Result.success(emptyList())
        return runCatching {
            withContext(Dispatchers.IO) {
                val accessToken = requireAccessToken()
                val metadata = fetchSpreadsheetMetadata(accessToken)
                val hgtSheetTitle = metadata.sheetTitleById[hgtSheetId]
                    ?: throw NonRetryableSyncException("Cannot resolve title for HGT_CHECKS sheetId=$hgtSheetId.")

                val gridRows = fetchGridRows(sheetTitle = hgtSheetTitle, accessToken = accessToken)
                if (gridRows.isEmpty()) return@withContext emptyList()
                val schema = parseHgtSchema(gridRows)

                gridRows
                    .drop(1)
                    .mapIndexedNotNull { index, rowValues ->
                        val rowNumber = index + 2
                        val maThietBi = schema.valueFromRow(rowValues, HgtCheckColumns.MA_THIET_BI).trim()
                        if (maThietBi.isBlank()) return@mapIndexedNotNull null

                        val rawCycle = schema.valueFromRow(rowValues, HgtCheckColumns.CHU_KY_NGAY).trim()
                        val chuKyNgay = rawCycle.toIntOrNull()
                        if (chuKyNgay == null || chuKyNgay <= 0) {
                            // Keep sync stable: skip malformed/incomplete row instead of failing all HGT pull.
                            return@mapIndexedNotNull null
                        }

                        val latestRaw = schema.valueFromRow(rowValues, HgtCheckColumns.LAN_GAN_NHAT).trim()
                        if (latestRaw.isBlank()) {
                            return@mapIndexedNotNull null
                        }
                        val lanGanNhat = DateTextFormatter.normalizeInputOrNull(latestRaw)
                            ?: DateTextFormatter.formatForDisplay(latestRaw)
                        if (lanGanNhat.isBlank() || lanGanNhat == "--") {
                            return@mapIndexedNotNull null
                        }

                        val nextRaw = schema.valueFromRow(rowValues, HgtCheckColumns.LAN_TIEP_THEO).trim()
                        val lanTiepTheo = DateTextFormatter.normalizeInputOrNull(nextRaw)
                            ?: if (nextRaw.isNotBlank()) DateTextFormatter.formatForDisplay(nextRaw) else ""
                        val calculatedNext = if (lanTiepTheo.isBlank()) {
                            HgtDateCalculator.calculateNextDate(lanGanNhat, chuKyNgay)
                        } else {
                            lanTiepTheo
                        }

                        val recordIdRaw = schema.valueFromRow(rowValues, HgtCheckColumns.RECORD_ID).trim()
                        val recordId = recordIdRaw.ifBlank { buildHgtFallbackId(maThietBi) }
                        val updatedAtRaw = schema.valueFromRow(rowValues, HgtCheckColumns.UPDATED_AT).trim()
                        val updatedAt = updatedAtRaw.toLongOrNull() ?: 0L

                        HgtCheck(
                            id = recordId,
                            maThietBi = maThietBi,
                            chuKyNgay = chuKyNgay,
                            lanGanNhat = lanGanNhat,
                            lanTiepTheo = calculatedNext,
                            updatedAt = updatedAt
                        )
                    }
            }
        }
    }

    suspend fun validateStructure(): Result<Unit> {
        if (sheetConfig.spreadsheetId.isBlank()) {
            return Result.failure(NonRetryableSyncException("Spreadsheet ID is not configured."))
        }

        val unmappedRoles = sheetConfig
            .missingSheetIdRoles(SheetContract.requiredRolesForSync)
            .map { it.name }
        if (unmappedRoles.isNotEmpty()) {
            return Result.failure(
                NonRetryableSyncException(
                    "Missing sheetId mapping for roles: ${unmappedRoles.joinToString(", ")}"
                )
            )
        }

        val duplicateSheetIds = sheetConfig.duplicateSheetIdRoles()
        if (duplicateSheetIds.isNotEmpty()) {
            val detail = duplicateSheetIds.entries.joinToString(" | ") { (sheetId, roles) ->
                "$sheetId -> ${roles.joinToString(", ") { it.name }}"
            }
            return Result.failure(
                NonRetryableSyncException("Duplicate sheetId mapping across roles: $detail")
            )
        }

        val columnValidationFailures = SheetContract.requiredColumnsByRole.mapNotNull { (role, requiredColumns) ->
            val configuredColumns = sheetConfig.requiredColumns(role)
            val missingColumns = requiredColumns.filterNot { configuredColumns.contains(it) }
            if (missingColumns.isEmpty()) {
                null
            } else {
                "${role.name}: ${missingColumns.joinToString(", ")}"
            }
        }
        if (columnValidationFailures.isNotEmpty()) {
            return Result.failure(
                NonRetryableSyncException(
                    "Missing required columns in config: ${columnValidationFailures.joinToString(" | ")}"
                )
            )
        }

        return Result.success(Unit)
    }

    private fun validateDmbtLogPayload(logs: List<DeviceLog>): Result<Unit> {
        val requiredColumns = sheetConfig.requiredColumns(SheetRole.DMBT_LOG)
        if (requiredColumns.isEmpty()) {
            return Result.failure(
                NonRetryableSyncException("No required column contract for role DMBT_LOG.")
            )
        }

        val invalidRows = logs.mapNotNull { log ->
            val row = log.toDmbtLogRow()
            val missingColumns = requiredColumns.filterNot { column ->
                row.containsKey(column)
            }
            if (missingColumns.isEmpty()) {
                null
            } else {
                "${log.recordId}: ${missingColumns.joinToString(", ")}"
            }
        }

        if (invalidRows.isNotEmpty()) {
            return Result.failure(
                NonRetryableSyncException(
                    "DMBT_LOG payload is missing required columns. ${invalidRows.joinToString(" | ")}"
                )
            )
        }

        val logsWithBlankKeyFields = logs.filter {
            it.recordId.isBlank() || it.maThietBi.isBlank() || it.ngayPhatHien.isBlank()
        }
        if (logsWithBlankKeyFields.isNotEmpty()) {
            return Result.failure(
                NonRetryableSyncException(
                    "DMBT_LOG payload has blank key fields for record IDs: " +
                        logsWithBlankKeyFields.joinToString(", ") { it.recordId.ifBlank { "<blank>" } }
                )
            )
        }

        return Result.success(Unit)
    }

    private fun requireAccessToken(): String {
        if (sheetConfig.canRefreshAccessToken) {
            return refreshAccessTokenIfNeeded()
        }

        val token = sheetConfig.accessToken
        if (token.isBlank()) {
            throw NonRetryableSyncException(
                "Sheets auth is missing. Set SHEETS_ACCESS_TOKEN, or configure SHEETS_OAUTH_CLIENT_ID and SHEETS_REFRESH_TOKEN."
            )
        }
        return token
    }

    @Synchronized
    private fun refreshAccessTokenIfNeeded(): String {
        val now = System.currentTimeMillis()
        cachedAccessToken
            ?.takeIf { it.isNotBlank() && now < cachedAccessTokenExpiresAt }
            ?.let { return it }

        val tokenResponse = requestAccessTokenFromRefreshToken()
        val accessToken = tokenResponse.optString("access_token").trim()
        if (accessToken.isBlank()) {
            throw NonRetryableSyncException("Google OAuth refresh did not return an access token.")
        }

        val expiresInSeconds = tokenResponse.optLong("expires_in", DEFAULT_ACCESS_TOKEN_TTL_SECONDS)
        val refreshAfterSeconds = (expiresInSeconds - ACCESS_TOKEN_EXPIRY_SKEW_SECONDS)
            .coerceAtLeast(MIN_ACCESS_TOKEN_TTL_SECONDS)
        cachedAccessToken = accessToken
        cachedAccessTokenExpiresAt = now + refreshAfterSeconds * 1000L
        return accessToken
    }

    private fun requestAccessTokenFromRefreshToken(): JSONObject {
        val endpoint = "https://oauth2.googleapis.com/token"
        val params = linkedMapOf(
            "client_id" to sheetConfig.oauthClientId,
            "refresh_token" to sheetConfig.refreshToken,
            "grant_type" to "refresh_token"
        )
        if (sheetConfig.oauthClientSecret.isNotBlank()) {
            params["client_secret"] = sheetConfig.oauthClientSecret
        }
        val formBody = params.entries.joinToString("&") { (key, value) ->
            "${formEncode(key)}=${formEncode(value)}"
        }

        val connection = (java.net.URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        }

        try {
            val bytes = formBody.toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }

            val responseCode = connection.responseCode
            val isSuccess = responseCode in 200..299
            val responseText = if (isSuccess) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }

            if (!isSuccess) {
                throw NonRetryableSyncException(
                    "Google OAuth refresh failed (HTTP $responseCode): ${parseGoogleErrorMessage(responseText)}"
                )
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (ioe: IOException) {
            throw IOException("Google OAuth refresh request failed: ${ioe.message}", ioe)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchSpreadsheetMetadata(accessToken: String): SpreadsheetMetadata {
        val endpoint = "https://sheets.googleapis.com/v4/spreadsheets/${sheetConfig.spreadsheetId}" +
            "?fields=sheets(properties(sheetId,title))"
        val body = executeJsonRequest(
            method = "GET",
            endpoint = endpoint,
            accessToken = accessToken
        )

        val sheetTitleById = mutableMapOf<Int, String>()
        val sheets = body.optJSONArray("sheets") ?: JSONArray()
        for (i in 0 until sheets.length()) {
            val sheet = sheets.optJSONObject(i) ?: continue
            val properties = sheet.optJSONObject("properties") ?: continue
            val sheetId = properties.optInt("sheetId", Int.MIN_VALUE)
            val title = properties.optString("title", "").trim()
            if (sheetId != Int.MIN_VALUE && title.isNotBlank()) {
                sheetTitleById[sheetId] = title
            }
        }
        return SpreadsheetMetadata(sheetTitleById = sheetTitleById)
    }

    private fun fetchGridRows(sheetTitle: String, accessToken: String): List<List<String>> {
        val range = "$sheetTitle!A1:ZZ"
        val endpoint = "https://sheets.googleapis.com/v4/spreadsheets/${sheetConfig.spreadsheetId}/values/${urlEncodePath(range)}" +
            "?majorDimension=ROWS"

        val body = executeJsonRequest(
            method = "GET",
            endpoint = endpoint,
            accessToken = accessToken
        )

        val values = body.optJSONArray("values") ?: JSONArray()
        val rows = mutableListOf<List<String>>()
        for (i in 0 until values.length()) {
            val row = values.optJSONArray(i) ?: JSONArray()
            val rowValues = mutableListOf<String>()
            for (j in 0 until row.length()) {
                rowValues += row.optString(j, "")
            }
            rows += rowValues
        }
        return rows
    }

    private fun batchUpdateRows(
        sheetTitle: String,
        maxColumnLabel: String,
        rowUpdates: List<Pair<Int, List<String>>>,
        accessToken: String
    ) {
        val endpoint = "https://sheets.googleapis.com/v4/spreadsheets/${sheetConfig.spreadsheetId}/values:batchUpdate"
        val dataItems = JSONArray()
        rowUpdates.forEach { (rowNumber, rowValues) ->
            val range = "$sheetTitle!A$rowNumber:${maxColumnLabel}$rowNumber"
            dataItems.put(
                JSONObject().apply {
                    put("range", range)
                    put("majorDimension", "ROWS")
                    put("values", JSONArray().put(JSONArray(rowValues)))
                }
            )
        }
        val body = JSONObject().apply {
            put("valueInputOption", "RAW")
            put("data", dataItems)
        }
        executeJsonRequest(
            method = "POST",
            endpoint = endpoint,
            accessToken = accessToken,
            requestBody = body
        )
    }

    private fun appendRows(
        sheetTitle: String,
        maxColumnLabel: String,
        rows: List<List<String>>,
        accessToken: String
    ) {
        val range = "$sheetTitle!A:${maxColumnLabel}"
        val endpoint =
            "https://sheets.googleapis.com/v4/spreadsheets/${sheetConfig.spreadsheetId}/values/${urlEncodePath(range)}:append" +
                "?valueInputOption=RAW&insertDataOption=INSERT_ROWS"

        val allRows = JSONArray()
        rows.forEach { rowValues -> allRows.put(JSONArray(rowValues)) }
        val body = JSONObject().apply {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", allRows)
        }

        executeJsonRequest(
            method = "POST",
            endpoint = endpoint,
            accessToken = accessToken,
            requestBody = body
        )
    }

    private fun deleteRows(
        sheetId: Int,
        rowNumbers: List<Int>,
        accessToken: String
    ) {
        if (rowNumbers.isEmpty()) return
        val endpoint = "https://sheets.googleapis.com/v4/spreadsheets/${sheetConfig.spreadsheetId}:batchUpdate"
        val requests = JSONArray()
        rowNumbers.sortedDescending().forEach { rowNumber ->
            requests.put(
                JSONObject().put(
                    "deleteDimension",
                    JSONObject().put(
                        "range",
                        JSONObject()
                            .put("sheetId", sheetId)
                            .put("dimension", "ROWS")
                            .put("startIndex", rowNumber - 1)
                            .put("endIndex", rowNumber)
                    )
                )
            )
        }
        val body = JSONObject().apply {
            put("requests", requests)
        }
        executeJsonRequest(
            method = "POST",
            endpoint = endpoint,
            accessToken = accessToken,
            requestBody = body
        )
    }

    private fun executeJsonRequest(
        method: String,
        endpoint: String,
        accessToken: String,
        requestBody: JSONObject? = null
    ): JSONObject {
        val connection = (java.net.URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (requestBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (requestBody != null) {
                val bytes = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
                connection.outputStream.use { it.write(bytes) }
            }

            val responseCode = connection.responseCode
            val isSuccess = responseCode in 200..299
            val responseText = if (isSuccess) {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                throw classifyHttpError(responseCode, errorText)
            }

            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (ioe: IOException) {
            throw IOException("Sheets API request failed: ${ioe.message}", ioe)
        } finally {
            connection.disconnect()
        }
    }

    private fun classifyHttpError(statusCode: Int, rawBody: String?): Throwable {
        val message = parseGoogleErrorMessage(rawBody)
        return when (statusCode) {
            400, 401, 403, 404 -> NonRetryableSyncException(
                buildUserFacingHttpError(statusCode = statusCode, apiMessage = message)
            )
            else -> IOException("Sheets API returned HTTP $statusCode: $message")
        }
    }

    private fun buildUserFacingHttpError(statusCode: Int, apiMessage: String): String {
        return when (statusCode) {
            401 -> {
                "Google Sheets token đã hết hạn hoặc không hợp lệ (HTTP 401). " +
                    "Vui lòng cập nhật SHEETS_ACCESS_TOKEN và cài lại bản debug."
            }
            403 -> {
                "Tài khoản chưa có quyền truy cập Google Sheet hoặc thiếu scope Sheets (HTTP 403). " +
                    "Vui lòng kiểm tra quyền chia sẻ file cho tài khoản đã cấp token."
            }
            404 -> {
                "Không tìm thấy spreadsheet hoặc sheetId đang cấu hình (HTTP 404). " +
                    "Vui lòng kiểm tra lại SHEETS_SPREADSHEET_ID và các SHEETS_*_SHEET_ID."
            }
            400 -> {
                "Yêu cầu gửi lên Google Sheets không hợp lệ (HTTP 400). Chi tiết: $apiMessage"
            }
            else -> "Sheets API returned HTTP $statusCode: $apiMessage"
        }
    }

    private fun parseGoogleErrorMessage(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) return "empty error body"
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: rawBody
        } catch (_: Throwable) {
            rawBody
        }
    }

    private fun parseHgtSchema(gridRows: List<List<String>>): HgtSheetSchema {
        val rawHeaders = gridRows.first().map { it.trim() }
        if (rawHeaders.isEmpty()) {
            throw NonRetryableSyncException("HGT_CHECKS header row is empty.")
        }
        val normalizedLooseHeaders = rawHeaders.map(::normalizeLooseHeader)
        val duplicateHeaders = normalizedLooseHeaders
            .filter { it.isNotBlank() }
            .groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        if (duplicateHeaders.isNotEmpty()) {
            throw NonRetryableSyncException(
                "Duplicate headers found in HGT_CHECKS: ${duplicateHeaders.joinToString(", ")}"
            )
        }

        val aliasByColumn = mapOf(
            HgtCheckColumns.RECORD_ID to setOf("record_id", "id"),
            HgtCheckColumns.MA_THIET_BI to setOf("ma_thiet_bi", "thiet_bi", "thietbi", "ma_thietbi"),
            HgtCheckColumns.CHU_KY_NGAY to setOf("chu_ky_ngay", "chu_ki_ngay", "chu_ky", "chu_ki", "chu_ki_ngay", "chu_ki_ngay_"),
            HgtCheckColumns.LAN_GAN_NHAT to setOf("lan_gan_nhat", "lan_kiem_tra_gan_nhat", "lan_gan_nhat_"),
            HgtCheckColumns.LAN_TIEP_THEO to setOf("lan_tiep_theo", "lan_kiem_tra_tiep_theo", "lan_tiep_theo_"),
            HgtCheckColumns.UPDATED_AT to setOf("updated_at")
        )

        val headerIndexByColumn = aliasByColumn.mapValues { (_, aliases) ->
            normalizedLooseHeaders.indexOfFirst { aliases.contains(it) }.takeIf { it >= 0 }
        }

        val requiredMissing = listOf(
            HgtCheckColumns.MA_THIET_BI,
            HgtCheckColumns.CHU_KY_NGAY,
            HgtCheckColumns.LAN_GAN_NHAT,
            HgtCheckColumns.LAN_TIEP_THEO
        ).filter { headerIndexByColumn[it] == null }
        if (requiredMissing.isNotEmpty()) {
            throw NonRetryableSyncException(
                "HGT_CHECKS missing required columns: ${requiredMissing.joinToString(", ")}"
            )
        }

        val rowByRecordId = mutableMapOf<String, Int>()
        val rowByDeviceCode = mutableMapOf<String, Int>()
        val rowValuesByRowNumber = mutableMapOf<Int, List<String>>()

        val recordIdIndex = headerIndexByColumn[HgtCheckColumns.RECORD_ID]
        val deviceCodeIndex = headerIndexByColumn[HgtCheckColumns.MA_THIET_BI]
            ?: throw NonRetryableSyncException("HGT_CHECKS missing required column: ${HgtCheckColumns.MA_THIET_BI}")

        gridRows.drop(1).forEachIndexed { index, row ->
            val rowNumber = index + 2
            rowValuesByRowNumber[rowNumber] = row

            if (recordIdIndex != null) {
                val recordId = row.getOrNull(recordIdIndex).orEmpty().trim()
                if (recordId.isNotBlank()) {
                    rowByRecordId[recordId] = rowNumber
                }
            }

            val deviceCode = row.getOrNull(deviceCodeIndex).orEmpty().trim()
            if (deviceCode.isNotBlank()) {
                rowByDeviceCode[normalizeDeviceCodeKey(deviceCode)] = rowNumber
            }
        }

        return HgtSheetSchema(
            rawHeaders = rawHeaders,
            headerIndexByColumn = headerIndexByColumn.mapNotNull { (column, index) ->
                index?.let { column to it }
            }.toMap(),
            rowByRecordId = rowByRecordId,
            rowByDeviceCode = rowByDeviceCode,
            rowValuesByRowNumber = rowValuesByRowNumber
        )
    }

    private fun ensureHeadersValid(normalizedHeaders: List<String>) {
        if (normalizedHeaders.isEmpty()) {
            throw NonRetryableSyncException("Header row is empty.")
        }
        val duplicates = normalizedHeaders
            .filter { it.isNotBlank() }
            .groupBy { it }
            .filter { (_, items) -> items.size > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            throw NonRetryableSyncException(
                "Duplicate headers found in DMBT_LOG: ${duplicates.joinToString(", ")}"
            )
        }
    }

    private fun ensureRequiredColumnsPresent(normalizedHeaders: List<String>, role: SheetRole) {
        val requiredColumns = sheetConfig.requiredColumns(role)
        val missingColumns = requiredColumns.filterNot { normalizedHeaders.contains(it) }
        if (missingColumns.isNotEmpty()) {
            throw NonRetryableSyncException(
                "Sheet ${role.name} is missing required columns: ${missingColumns.joinToString(", ")}"
            )
        }
    }

    private fun alignToSheetHeaders(rowByContractColumn: Map<String, String>, rawHeaders: List<String>): List<String> {
        return rawHeaders.map { rawHeader ->
            val normalized = normalizeHeader(rawHeader)
            rowByContractColumn[normalized].orEmpty()
        }
    }

    private fun toNormalizedRowMap(normalizedHeaders: List<String>, rowValues: List<String>): Map<String, String> {
        return normalizedHeaders.mapIndexed { index, header ->
            header to rowValues.getOrNull(index).orEmpty().trim()
        }.toMap()
    }

    private fun normalizeHeader(value: String): String = value.trim().lowercase()

    private fun normalizeLooseHeader(value: String): String {
        val withoutAccent = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutAccent
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun normalizeDeviceCodeKey(value: String): String =
        value.trim().lowercase(Locale.ROOT)

    private fun normalizeKeyText(value: String): String {
        return Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun buildDmbtMatchKey(
        maThietBi: String,
        ngayPhatHien: String,
        hangMuc: String,
        tinhTrangThietBi: String
    ): String {
        val normalizedDate = DateTextFormatter.formatForDisplay(ngayPhatHien)
        val parts = listOf(
            normalizeKeyText(maThietBi),
            normalizeKeyText(normalizedDate),
            normalizeKeyText(hangMuc),
            normalizeKeyText(tinhTrangThietBi)
        )
        return parts.joinToString("|")
    }

    private fun buildDmbtFallbackRecordId(
        maThietBi: String,
        ngayPhatHien: String,
        hangMuc: String,
        tinhTrangThietBi: String,
        rowNumber: Int
    ): String {
        val base = buildDmbtMatchKey(
            maThietBi = maThietBi,
            ngayPhatHien = ngayPhatHien,
            hangMuc = hangMuc,
            tinhTrangThietBi = tinhTrangThietBi
        )
        val compact = base.replace("|", "-").ifBlank { "row-$rowNumber-${UUID.randomUUID()}" }
        return "dmbt-auto-$compact"
    }

    private fun buildHgtFallbackId(deviceCode: String): String =
        "hgt-auto-${normalizeDeviceCodeKey(deviceCode).replace(Regex("[^a-z0-9]+"), "-").trim('-')}"

    private fun parseDmbtSchema(gridRows: List<List<String>>): DmbtSheetSchema {
        if (gridRows.isEmpty()) {
            throw NonRetryableSyncException("DMBT_LOG sheet is empty.")
        }

        val aliasByColumn = mapOf(
            DmbtLogColumns.RECORD_ID to setOf("record_id", "id"),
            DmbtLogColumns.MA_THIET_BI to setOf("ma_thiet_bi", "ma_thiet_bi_", "ma_thietbi", "ma_thiet_bi__"),
            DmbtLogColumns.HANG_MUC to setOf("hang_muc", "hang_muc_"),
            DmbtLogColumns.NGUOI_BAO_CAO to setOf("nguoi_bao_cao", "nguoi_bao_cao_"),
            DmbtLogColumns.TINH_TRANG_THIET_BI to setOf("tinh_trang_thiet_bi", "tinh_trang_thiet_bi_"),
            DmbtLogColumns.KTV_PHU_TRACH to setOf("ktv_phu_trach", "ktv_phu_trach_nhan_thong_tin", "ktv_phu_trach_"),
            DmbtLogColumns.NGAY_PHAT_HIEN to setOf("ngay_phat_hien", "ngay_phat_hien_"),
            DmbtLogColumns.NGAY_SUA_CHUA to setOf("ngay_sua_chua", "ngay_sua_chua_"),
            DmbtLogColumns.GHI_CHU to setOf("ghi_chu", "ghi_chu_"),
            DmbtLogColumns.UPDATED_AT to setOf("updated_at")
        )
        val requiredCoreColumns = listOf(
            DmbtLogColumns.MA_THIET_BI,
            DmbtLogColumns.HANG_MUC,
            DmbtLogColumns.NGUOI_BAO_CAO,
            DmbtLogColumns.TINH_TRANG_THIET_BI,
            DmbtLogColumns.KTV_PHU_TRACH,
            DmbtLogColumns.NGAY_PHAT_HIEN,
            DmbtLogColumns.NGAY_SUA_CHUA,
            DmbtLogColumns.GHI_CHU
        )

        data class HeaderCandidate(
            val rowIndex: Int,
            val rawHeaders: List<String>,
            val normalizedLooseHeaders: List<String>,
            val headerIndexByColumn: Map<String, Int>,
            val score: Int
        )

        val candidates = gridRows
            .take(12)
            .mapIndexedNotNull { rowIndex, row ->
                val rawHeaders = row.map { it.trim() }
                if (rawHeaders.isEmpty()) return@mapIndexedNotNull null
                val normalizedLooseHeaders = rawHeaders.map(::normalizeLooseHeader)
                val headerIndexByColumn = aliasByColumn.mapValues { (_, aliases) ->
                    normalizedLooseHeaders.indexOfFirst { aliases.contains(it) }.takeIf { it >= 0 }
                }
                    .mapNotNull { (column, index) ->
                        index?.let { column to it }
                    }
                    .toMap()

                val score = requiredCoreColumns.count { headerIndexByColumn[it] != null }
                val hasKeyColumn = headerIndexByColumn[DmbtLogColumns.MA_THIET_BI] != null
                if (!hasKeyColumn || score < 5) return@mapIndexedNotNull null

                HeaderCandidate(
                    rowIndex = rowIndex,
                    rawHeaders = rawHeaders,
                    normalizedLooseHeaders = normalizedLooseHeaders,
                    headerIndexByColumn = headerIndexByColumn,
                    score = score
                )
            }

        val header = candidates
            .sortedWith(compareByDescending<HeaderCandidate> { it.score }.thenBy { it.rowIndex })
            .firstOrNull()
            ?: throw NonRetryableSyncException(
                "Cannot detect DMBT_LOG header row. Expected Vietnamese/standard columns like 'Mã thiết bị', 'Ngày phát hiện', 'Ghi chú'."
            )

        val duplicateHeaders = header.normalizedLooseHeaders
            .filter { it.isNotBlank() }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateHeaders.isNotEmpty()) {
            throw NonRetryableSyncException(
                "Duplicate headers found in DMBT_LOG: ${duplicateHeaders.joinToString(", ")}"
            )
        }

        val missingCore = requiredCoreColumns.filter { header.headerIndexByColumn[it] == null }
        if (missingCore.isNotEmpty()) {
            throw NonRetryableSyncException(
                "DMBT_LOG missing required columns: ${missingCore.joinToString(", ")}"
            )
        }

        val rows = gridRows.drop(header.rowIndex + 1).mapIndexed { dataIndex, values ->
            DmbtDataRow(
                rowNumber = header.rowIndex + 2 + dataIndex,
                values = values
            )
        }

        val rowByRecordId = mutableMapOf<String, Int>()
        val rowByFallbackKey = mutableMapOf<String, Int>()
        val ambiguousFallbackKeys = mutableSetOf<String>()
        val rowValuesByRowNumber = mutableMapOf<Int, List<String>>()

        rows.forEach { row ->
            rowValuesByRowNumber[row.rowNumber] = row.values
            val recordIdIndex = header.headerIndexByColumn[DmbtLogColumns.RECORD_ID]
            if (recordIdIndex != null) {
                val recordId = row.values.getOrNull(recordIdIndex).orEmpty().trim()
                if (recordId.isNotBlank()) {
                    rowByRecordId[recordId] = row.rowNumber
                }
            }

            val maThietBi = row.values.getOrNull(header.headerIndexByColumn.getValue(DmbtLogColumns.MA_THIET_BI)).orEmpty()
            val ngayPhatHien = row.values.getOrNull(header.headerIndexByColumn.getValue(DmbtLogColumns.NGAY_PHAT_HIEN)).orEmpty()
            val hangMuc = row.values.getOrNull(header.headerIndexByColumn.getValue(DmbtLogColumns.HANG_MUC)).orEmpty()
            val tinhTrang = row.values.getOrNull(header.headerIndexByColumn.getValue(DmbtLogColumns.TINH_TRANG_THIET_BI)).orEmpty()
            val key = buildDmbtMatchKey(
                maThietBi = maThietBi,
                ngayPhatHien = ngayPhatHien,
                hangMuc = hangMuc,
                tinhTrangThietBi = tinhTrang
            )
            if (key.isNotBlank() && key != "|||") {
                if (rowByFallbackKey.containsKey(key)) {
                    rowByFallbackKey.remove(key)
                    ambiguousFallbackKeys += key
                } else if (!ambiguousFallbackKeys.contains(key)) {
                    rowByFallbackKey[key] = row.rowNumber
                }
            }
        }

        return DmbtSheetSchema(
            rawHeaders = header.rawHeaders,
            headerIndexByColumn = header.headerIndexByColumn,
            rows = rows,
            rowByRecordId = rowByRecordId,
            rowByFallbackKey = rowByFallbackKey,
            ambiguousFallbackKeys = ambiguousFallbackKeys,
            rowValuesByRowNumber = rowValuesByRowNumber
        )
    }

    private fun urlEncodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun formEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private data class SpreadsheetMetadata(
        val sheetTitleById: Map<Int, String>
    )

    private data class DmbtDataRow(
        val rowNumber: Int,
        val values: List<String>
    )

    private data class DmbtPulledLogRow(
        val rowNumber: Int,
        val log: DeviceLog
    )

    private data class DmbtSheetSchema(
        val rawHeaders: List<String>,
        val headerIndexByColumn: Map<String, Int>,
        val rows: List<DmbtDataRow>,
        val rowByRecordId: Map<String, Int>,
        val rowByFallbackKey: Map<String, Int>,
        val ambiguousFallbackKeys: Set<String>,
        val rowValuesByRowNumber: Map<Int, List<String>>
    ) {
        fun valueFromRow(rowValues: List<String>, column: String): String {
            val index = headerIndexByColumn[column] ?: return ""
            return rowValues.getOrNull(index).orEmpty().trim()
        }

        fun findExistingRow(
            log: DeviceLog,
            keyBuilder: (String, String, String, String) -> String
        ): Int? {
            val fallbackKey = keyBuilder(
                log.maThietBi,
                log.ngayPhatHien,
                log.hangMuc,
                log.tinhTrangThietBi
            )
            return rowByRecordId[log.recordId]
                ?: resolveFallbackRowNumber(
                    fallbackKey = fallbackKey,
                    rowByFallbackKey = rowByFallbackKey,
                    ambiguousFallbackKeys = ambiguousFallbackKeys
                )
        }

        fun mergeRowValues(existingRow: List<String>, rowByContractColumn: Map<String, String>): List<String> {
            val merged = MutableList(rawHeaders.size) { index ->
                existingRow.getOrNull(index).orEmpty()
            }
            headerIndexByColumn.forEach { (column, index) ->
                merged[index] = rowByContractColumn[column].orEmpty()
            }
            return merged
        }

        fun buildAppendRowValues(rowByContractColumn: Map<String, String>): List<String> {
            val row = MutableList(rawHeaders.size) { "" }
            headerIndexByColumn.forEach { (column, index) ->
                row[index] = rowByContractColumn[column].orEmpty()
            }
            return row
        }
    }

    private data class HgtSheetSchema(
        val rawHeaders: List<String>,
        val headerIndexByColumn: Map<String, Int>,
        val rowByRecordId: Map<String, Int>,
        val rowByDeviceCode: Map<String, Int>,
        val rowValuesByRowNumber: Map<Int, List<String>>
    ) {
        fun valueFromRow(rowValues: List<String>, column: String): String {
            val index = headerIndexByColumn[column] ?: return ""
            return rowValues.getOrNull(index).orEmpty().trim()
        }

        fun findExistingRow(check: HgtCheck): Int? {
            return rowByRecordId[check.id] ?: rowByDeviceCode[check.maThietBi.trim().lowercase(Locale.ROOT)]
        }

        fun mergeRowValues(existingRow: List<String>, rowByContractColumn: Map<String, String>): List<String> {
            val merged = MutableList(rawHeaders.size) { index ->
                existingRow.getOrNull(index).orEmpty()
            }
            headerIndexByColumn.forEach { (column, index) ->
                merged[index] = rowByContractColumn[column].orEmpty()
            }
            return merged
        }

        fun buildAppendRowValues(rowByContractColumn: Map<String, String>): List<String> {
            val row = MutableList(rawHeaders.size) { "" }
            headerIndexByColumn.forEach { (column, index) ->
                row[index] = rowByContractColumn[column].orEmpty()
            }
            return row
        }
    }

    internal data class RepairDataRow(
        val rowNumber: Int,
        val values: List<String>
    )

    internal enum class RepairSheetMode {
        TECHNICAL,
        REAL_DMBT_STYLE
    }

    internal data class RepairSheetSchema(
        val rawHeaders: List<String>,
        val headerIndexByColumn: Map<String, Int>,
        val rowByRecordId: Map<String, Int>,
        val rowValuesByRowNumber: Map<Int, List<String>>,
        val rows: List<RepairDataRow>,
        val headerRowIndex: Int,
        val mode: RepairSheetMode
    ) {
        fun valueFromRow(rowValues: List<String>, column: String): String {
            val index = headerIndexByColumn[column] ?: return ""
            return rowValues.getOrNull(index).orEmpty().trim()
        }
    }

    suspend fun pullRepairLogs(optional: Boolean = false): Result<List<DmbtRepairUpdate>> {
        val repairSheetId = sheetConfig.sheetId(SheetRole.DMBT_REPAIR_LOG)
            ?: return Result.success(emptyList())

        val result = runCatching {
            withContext(Dispatchers.IO) {
                val accessToken = requireAccessToken()
                val metadata = fetchSpreadsheetMetadata(accessToken)
                val repairSheetTitle = metadata.sheetTitleById[repairSheetId]
                    ?: throw NonRetryableSyncException(
                        "Cannot resolve title for DMBT_REPAIR_LOG sheetId=$repairSheetId."
                    )

                val gridRows = fetchGridRows(sheetTitle = repairSheetTitle, accessToken = accessToken)
                if (gridRows.isEmpty()) return@withContext emptyList()

                parseRepairRows(gridRows)
            }
        }
        val resolved = resolveOptionalRepairPullResult(result, optional)
        if (optional && result.isFailure) {
            val message = result.exceptionOrNull()?.message ?: "unknown repair pull error"
            Log.w(TAG, "pullRepairLogs optional failed: $message")
        }
        return resolved
    }

    companion object {
        private const val TAG = "SheetsRemoteDataSource"
        private const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600L
        private const val ACCESS_TOKEN_EXPIRY_SKEW_SECONDS = 120L
        private const val MIN_ACCESS_TOKEN_TTL_SECONDS = 60L

        internal fun buildNamespacedDmbtRecordId(sheetId: Int, recordId: String): String =
            "readonly-dmbt-$sheetId-${recordId.trim()}"

        internal fun dmbtPulledRecordIdentity(
            sheetId: Int,
            baseRecordId: String,
            namespaceRecordIds: Boolean
        ): DmbtPulledRecordIdentity {
            val recordId = if (namespaceRecordIds) {
                buildNamespacedDmbtRecordId(sheetId = sheetId, recordId = baseRecordId)
            } else {
                baseRecordId
            }
            return DmbtPulledRecordIdentity(recordId = recordId, sourceSheetId = sheetId)
        }

        internal fun buildDmbtPullSheetStats(
            sheetId: Int,
            sheetTitle: String,
            pulledLogs: List<DeviceLog>,
            skippedInvalidRows: Int,
            rowNumbersByRecordId: Map<String, List<Int>> = emptyMap(),
            skippedInvalidRowSamples: List<Int> = emptyList()
        ): DmbtPullSheetStats {
            val remoteIds = pulledLogs.map { it.recordId }
            val uniqueRemoteIds = remoteIds.toSet().size
            val duplicateRemoteIdSamples = remoteIds
                .groupingBy { it }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .take(10)
            val duplicateRemoteRowSamples = duplicateRemoteIdSamples.map { recordId ->
                DmbtDuplicateRemoteIdSample(
                    recordId = recordId,
                    rowNumbers = rowNumbersByRecordId[recordId].orEmpty()
                )
            }
            return DmbtPullSheetStats(
                sheetId = sheetId,
                sheetTitle = sheetTitle,
                fetchedRows = pulledLogs.size,
                uniqueRemoteIds = uniqueRemoteIds,
                duplicateRemoteIds = pulledLogs.size - uniqueRemoteIds,
                duplicateRemoteIdSamples = duplicateRemoteIdSamples,
                duplicateRemoteRowSamples = duplicateRemoteRowSamples,
                skippedInvalidRows = skippedInvalidRows
                    .coerceAtLeast(skippedInvalidRowSamples.size),
                skippedInvalidRowSamples = skippedInvalidRowSamples.take(10)
            )
        }

        internal fun buildDmbtSheetIssueReports(
            sheetTitle: String,
            stats: DmbtPullSheetStats,
            pulledLogs: List<DeviceLog>
        ): List<DmbtSheetIssueReport> {
            val firstLogByRecordId = pulledLogs.associateBy { it.recordId }
            val duplicateIssues = stats.duplicateRemoteRowSamples.map { sample ->
                val log = firstLogByRecordId[sample.recordId]
                DmbtSheetIssueReport(
                    type = DmbtSheetIssueType.DUPLICATE_IDENTITY,
                    sheetId = stats.sheetId,
                    sheetTitle = sheetTitle,
                    deviceCode = log?.maThietBi.orEmpty(),
                    discoveryDate = log?.ngayPhatHien.orEmpty(),
                    description = log?.tinhTrangThietBi.orEmpty(),
                    rowNumbers = sample.rowNumbers
                )
            }
            val invalidDateIssues = stats.skippedInvalidRowSamples.map { rowNumber ->
                DmbtSheetIssueReport(
                    type = DmbtSheetIssueType.INVALID_DISCOVERY_DATE,
                    sheetId = stats.sheetId,
                    sheetTitle = sheetTitle,
                    deviceCode = "",
                    discoveryDate = "",
                    description = "",
                    rowNumbers = listOf(rowNumber)
                )
            }
            return (duplicateIssues + invalidDateIssues).take(20)
        }

        internal fun groupDmbtLogsByTargetSheet(
            logs: List<DeviceLog>,
            defaultCreateSheetId: Int?,
            configuredDmbtSheetIds: Set<Int> = emptySet()
        ): Map<Int, List<DeviceLog>> {
            val hasMultipleConfiguredSheets = configuredDmbtSheetIds.size > 1
            return logs
                .mapNotNull { log ->
                    val targetSheetId = when {
                        log.sourceSheetId != null -> {
                            val sourceSheetId = log.sourceSheetId
                            if (configuredDmbtSheetIds.isEmpty() || configuredDmbtSheetIds.contains(sourceSheetId)) {
                                sourceSheetId
                            } else {
                                null
                            }
                        }
                        else -> {
                            val recordSheetId = extractReadonlyDmbtSheetId(log.recordId)
                            when {
                                recordSheetId != null &&
                                    (configuredDmbtSheetIds.isEmpty() || configuredDmbtSheetIds.contains(recordSheetId)) ->
                                    recordSheetId
                                defaultCreateSheetId != null &&
                                    (!hasMultipleConfiguredSheets || isSafeForDefaultRouting(log.recordId)) ->
                                    defaultCreateSheetId
                                else -> null
                            }
                        }
                    } ?: return@mapNotNull null
                    targetSheetId to log
                }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .toSortedMap()
        }

        internal fun dmbtPullTargets(bindings: List<SheetConfig.DmbtSheetBinding>): List<DmbtPullTarget> {
            return bindings.map { binding ->
                DmbtPullTarget(
                    sheetId = binding.sheetId,
                    namespaceRecordIds = !binding.isDefaultCreateTarget
                )
            }
        }

        internal fun isYearlyDmbtSheetFailureFatal(sheetId: Int, yearlySheetIds: Set<Int>): Boolean {
            return yearlySheetIds.contains(sheetId)
        }

        internal fun resolveOptionalRepairPullResult(
            result: Result<List<DmbtRepairUpdate>>,
            optional: Boolean
        ): Result<List<DmbtRepairUpdate>> {
            return if (optional && result.isFailure && isOptionalRepairFailureRecoverable(result.exceptionOrNull())) {
                Result.success(emptyList())
            } else {
                result
            }
        }

        internal fun isOptionalRepairFailureRecoverable(error: Throwable?): Boolean {
            val message = error?.message?.lowercase(Locale.ROOT).orEmpty()
            return message.contains("cannot resolve title") ||
                message.contains("missing title") ||
                message.contains("sheetid") ||
                message.contains("sheet is empty")
        }

        internal fun validateDmbtProvenanceConfig(
            bindings: List<SheetConfig.DmbtSheetBinding>,
            yearlySheetIds: Set<Int>
        ): Result<Unit> {
            if (bindings.isEmpty()) {
                return Result.failure(
                    NonRetryableSyncException("Missing DMBT sheet bindings in config.")
                )
            }
            if (yearlySheetIds.isEmpty()) {
                return Result.failure(
                    NonRetryableSyncException(
                        "Missing yearly DMBT sheet bindings. Monthly-only config is not allowed for full DMBT sync."
                    )
                )
            }
            return Result.success(Unit)
        }

        internal fun dedupeDmbtLogsForPush(targetSheetId: Int, logs: List<DeviceLog>): List<DeviceLog> {
            val latestBySheetRecordId = linkedMapOf<String, DeviceLog>()
            logs.forEach { log ->
                val sheetRecordId = resolveDmbtSheetRecordId(
                    targetSheetId = targetSheetId,
                    recordId = log.recordId
                )
                val current = latestBySheetRecordId[sheetRecordId]
                if (current == null || log.updatedAt >= current.updatedAt) {
                    latestBySheetRecordId[sheetRecordId] = log
                }
            }
            val sorted = latestBySheetRecordId.values.toMutableList()
            sorted.sortWith(java.util.Comparator { left, right ->
                left.updatedAt.compareTo(right.updatedAt)
            })
            return sorted
        }

        internal fun resolveDmbtSheetRecordId(targetSheetId: Int, recordId: String): String {
            val trimmed = recordId.trim()
            val legacyPrefix = "readonly-dmbt-$targetSheetId-"
            if (trimmed.startsWith(legacyPrefix)) {
                return trimmed.removePrefix(legacyPrefix)
            }
            return trimmed
        }

        internal fun resolveFallbackRowNumber(
            fallbackKey: String,
            rowByFallbackKey: Map<String, Int>,
            ambiguousFallbackKeys: Set<String>
        ): Int? {
            if (ambiguousFallbackKeys.contains(fallbackKey)) {
                throw NonRetryableSyncException(
                    "Ambiguous DMBT fallback key for push: '$fallbackKey'. Skip append/update to avoid duplicate."
                )
            }
            return rowByFallbackKey[fallbackKey]
        }

        internal fun extractReadonlyDmbtSheetId(recordId: String): Int? {
            val match = Regex("^readonly-dmbt-(\\d+)-").find(recordId.trim()) ?: return null
            return match.groupValues.getOrNull(1)?.toIntOrNull()
        }

        private fun isSafeForDefaultRouting(recordId: String): Boolean {
            val trimmed = recordId.trim()
            return trimmed.startsWith("dmbt-auto-")
        }

        internal fun parsePulledDmbtRowsForTest(
            sheetId: Int,
            gridRows: List<List<String>>,
            namespaceRecordIds: Boolean
        ): List<DeviceLog> {
            if (gridRows.isEmpty()) return emptyList()
            val parser = TestDmbtPullParser(sheetId, namespaceRecordIds)
            return parser.parse(gridRows)
        }

        /**
         * Parses repair sheet schema from grid rows.
         * Internal helper for parseRepairRows.
         */
        internal fun parseRepairSchema(gridRows: List<List<String>>): RepairSheetSchema {
            if (gridRows.isEmpty()) {
                throw NonRetryableSyncException("DMBT_REPAIR_LOG sheet is empty.")
            }

            val aliasByColumn = mapOf(
                DmbtRepairLogColumns.RECORD_ID to setOf("record_id", "id"),
                DmbtRepairLogColumns.MA_THIET_BI to setOf("ma_thiet_bi", "ma_thiet_bi_", "ma_thietbi", "ma_thiet_bi__"),
                DmbtRepairLogColumns.NGAY_SUA_CHUA to setOf("ngay_sua_chua", "ngay_sua_chua_"),
                DmbtRepairLogColumns.GHI_CHU to setOf("ghi_chu", "ghi_chu_"),
                DmbtRepairLogColumns.UPDATED_AT to setOf("updated_at"),
                DmbtLogColumns.NGAY_PHAT_HIEN to setOf("ngay_phat_hien", "ngay_phat_hien_"),
                DmbtLogColumns.HANG_MUC to setOf("hang_muc", "hang_muc_"),
                DmbtLogColumns.TINH_TRANG_THIET_BI to setOf("tinh_trang_thiet_bi", "tinh_trang_thiet_bi_")
            )

            val technicalRequired = listOf(
                DmbtRepairLogColumns.RECORD_ID,
                DmbtRepairLogColumns.MA_THIET_BI,
                DmbtRepairLogColumns.NGAY_SUA_CHUA,
                DmbtRepairLogColumns.GHI_CHU,
                DmbtRepairLogColumns.UPDATED_AT
            )
            val realSheetRequired = listOf(
                DmbtRepairLogColumns.MA_THIET_BI,
                DmbtLogColumns.NGAY_PHAT_HIEN,
                DmbtLogColumns.HANG_MUC,
                DmbtLogColumns.TINH_TRANG_THIET_BI,
                DmbtRepairLogColumns.NGAY_SUA_CHUA,
                DmbtRepairLogColumns.GHI_CHU
            )

            data class RepairHeaderCandidate(
                val rowIndex: Int,
                val rawHeaders: List<String>,
                val normalizedHeaders: List<String>,
                val headerIndexByColumn: Map<String, Int>,
                val mode: RepairSheetMode,
                val score: Int
            )

            val candidates = gridRows
                .take(12)
                .mapIndexedNotNull { rowIndex, row ->
                    val rawHeaders = row.map { it.trim() }
                    if (rawHeaders.isEmpty()) return@mapIndexedNotNull null
                    val normalizedHeaders = rawHeaders.map(::normalizeLooseHeaderValue)
                    val headerIndexByColumn = aliasByColumn.mapValues { (_, aliases) ->
                        normalizedHeaders.indexOfFirst { aliases.contains(it) }.takeIf { it >= 0 }
                    }.mapNotNull { (column, index) ->
                        index?.let { column to it }
                    }.toMap()

                    val technicalScore = technicalRequired.count { headerIndexByColumn[it] != null }
                    val realScore = realSheetRequired.count { headerIndexByColumn[it] != null }
                    val hasTechnicalCore = headerIndexByColumn[DmbtRepairLogColumns.MA_THIET_BI] != null &&
                        headerIndexByColumn[DmbtRepairLogColumns.NGAY_SUA_CHUA] != null &&
                        headerIndexByColumn[DmbtRepairLogColumns.GHI_CHU] != null
                    val mode = when {
                        realScore == realSheetRequired.size -> RepairSheetMode.REAL_DMBT_STYLE
                        hasTechnicalCore && technicalScore >= 4 -> RepairSheetMode.TECHNICAL
                        else -> null
                    } ?: return@mapIndexedNotNull null

                    val score = when (mode) {
                        RepairSheetMode.TECHNICAL -> 200 + technicalScore
                        RepairSheetMode.REAL_DMBT_STYLE -> 100 + realScore
                    }
                    RepairHeaderCandidate(
                        rowIndex = rowIndex,
                        rawHeaders = rawHeaders,
                        normalizedHeaders = normalizedHeaders,
                        headerIndexByColumn = headerIndexByColumn,
                        mode = mode,
                        score = score
                    )
                }

            val header = candidates
                .sortedWith(compareByDescending<RepairHeaderCandidate> { it.score }.thenBy { it.rowIndex })
                .firstOrNull()
                ?: throw NonRetryableSyncException(
                    "Cannot detect DMBT_REPAIR_LOG header row. Expected repair technical columns or full DMBT-like columns."
                )

            val duplicateHeaders = header.normalizedHeaders
                .filter { it.isNotBlank() }
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
            if (duplicateHeaders.isNotEmpty()) {
                throw NonRetryableSyncException(
                    "Duplicate headers found in DMBT_REPAIR_LOG: ${duplicateHeaders.joinToString(", ")}"
                )
            }

            val missingRequired = when (header.mode) {
                RepairSheetMode.TECHNICAL -> technicalRequired.filter { header.headerIndexByColumn[it] == null }
                RepairSheetMode.REAL_DMBT_STYLE -> realSheetRequired.filter { header.headerIndexByColumn[it] == null }
            }
            if (missingRequired.isNotEmpty()) {
                throw NonRetryableSyncException(
                    "DMBT_REPAIR_LOG missing required columns for mode ${header.mode}: ${missingRequired.joinToString(", ")}"
                )
            }

            val rows = gridRows.drop(header.rowIndex + 1).mapIndexed { dataIndex, values ->
                RepairDataRow(
                    rowNumber = header.rowIndex + 2 + dataIndex,
                    values = values
                )
            }
            val rowByRecordId = mutableMapOf<String, Int>()
            val rowValuesByRowNumber = mutableMapOf<Int, List<String>>()
            val recordIdIndex = header.headerIndexByColumn[DmbtRepairLogColumns.RECORD_ID]
            rows.forEach { row ->
                rowValuesByRowNumber[row.rowNumber] = row.values
                val recordId = row.values.getOrNull(recordIdIndex ?: -1).orEmpty().trim()
                if (recordId.isNotBlank()) {
                    rowByRecordId[recordId] = row.rowNumber
                }
            }

            return RepairSheetSchema(
                rawHeaders = header.rawHeaders,
                headerIndexByColumn = header.headerIndexByColumn,
                rowByRecordId = rowByRecordId,
                rowValuesByRowNumber = rowValuesByRowNumber,
                rows = rows,
                headerRowIndex = header.rowIndex,
                mode = header.mode
            )
        }

        /**
         * Parses grid rows to DmbtRepairUpdate list.
         * Exposed for unit testing without network access.
         */
        internal fun parseRepairRows(gridRows: List<List<String>>): List<DmbtRepairUpdate> {
            if (gridRows.isEmpty()) return emptyList()

            val schema = parseRepairSchema(gridRows)

            return schema.rows
                .mapNotNull { rowValues ->
                    val row = rowValues.values
                    val recordId = schema.valueFromRow(row, DmbtRepairLogColumns.RECORD_ID)
                    val maThietBi = schema.valueFromRow(row, DmbtRepairLogColumns.MA_THIET_BI)
                    if (maThietBi.isBlank()) return@mapNotNull null

                    val ngaySuaRaw = schema.valueFromRow(row, DmbtRepairLogColumns.NGAY_SUA_CHUA)
                    val ngaySuaChua = ngaySuaRaw.takeIf { it.isNotBlank() }

                    val ghiChu = schema.valueFromRow(row, DmbtRepairLogColumns.GHI_CHU)
                    if (ngaySuaChua == null && ghiChu.isBlank()) return@mapNotNull null

                    val updatedAtRaw = schema.valueFromRow(row, DmbtRepairLogColumns.UPDATED_AT)
                    val updatedAt = updatedAtRaw.toLongOrNull() ?: 0L

                    DmbtRepairUpdate(
                        recordId = recordId,
                        maThietBi = maThietBi,
                        ngaySuaChua = ngaySuaChua,
                        ghiChu = ghiChu,
                        updatedAt = updatedAt,
                        ngayPhatHien = schema.valueFromRow(row, DmbtLogColumns.NGAY_PHAT_HIEN).ifBlank { null },
                        hangMuc = schema.valueFromRow(row, DmbtLogColumns.HANG_MUC).ifBlank { null },
                        tinhTrangThietBi = schema.valueFromRow(row, DmbtLogColumns.TINH_TRANG_THIET_BI).ifBlank { null }
                    )
                }
        }

        private fun normalizeLooseHeaderValue(value: String): String {
            val withoutAccent = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            return withoutAccent
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }

        private fun normalizeKeyTextForTest(value: String): String {
            return Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
        }

        private fun buildDmbtMatchKeyForTest(
            maThietBi: String,
            ngayPhatHien: String,
            hangMuc: String,
            tinhTrangThietBi: String
        ): String {
            val normalizedDate = DateTextFormatter.formatForDisplay(ngayPhatHien)
            return listOf(
                normalizeKeyTextForTest(maThietBi),
                normalizeKeyTextForTest(normalizedDate),
                normalizeKeyTextForTest(hangMuc),
                normalizeKeyTextForTest(tinhTrangThietBi)
            ).joinToString("|")
        }

        private fun buildDmbtFallbackRecordIdForTest(
            maThietBi: String,
            ngayPhatHien: String,
            hangMuc: String,
            tinhTrangThietBi: String,
            rowNumber: Int
        ): String {
            val base = buildDmbtMatchKeyForTest(maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi)
            val compact = base.replace("|", "-").ifBlank { "row-$rowNumber-${UUID.randomUUID()}" }
            return "dmbt-auto-$compact"
        }

        private class TestDmbtPullParser(
            private val sheetId: Int,
            private val namespaceRecordIds: Boolean
        ) {
            fun parse(gridRows: List<List<String>>): List<DeviceLog> {
                val header = detectHeader(gridRows)
                return gridRows
                    .drop(header.rowIndex + 1)
                    .mapIndexedNotNull { dataIndex, row ->
                        val rowNumber = header.rowIndex + 2 + dataIndex
                        val maThietBi = value(row, header, DmbtLogColumns.MA_THIET_BI).trim()
                        if (maThietBi.isBlank()) return@mapIndexedNotNull null
                        val hangMuc = value(row, header, DmbtLogColumns.HANG_MUC).trim()
                        val nguoiBaoCao = value(row, header, DmbtLogColumns.NGUOI_BAO_CAO).trim()
                        val tinhTrang = value(row, header, DmbtLogColumns.TINH_TRANG_THIET_BI).trim()
                        val ktvPhuTrach = value(row, header, DmbtLogColumns.KTV_PHU_TRACH).trim()
                        val ngayPhatHienRaw = value(row, header, DmbtLogColumns.NGAY_PHAT_HIEN).trim()
                        val ngayPhatHien = DateTextFormatter.normalizeInputOrNull(ngayPhatHienRaw)
                            ?: DateTextFormatter.formatForDisplay(ngayPhatHienRaw)
                        if (ngayPhatHien.isBlank() || ngayPhatHien == "--") return@mapIndexedNotNull null
                        val ngaySuaRaw = value(row, header, DmbtLogColumns.NGAY_SUA_CHUA).trim()
                        val ngaySua = DateTextFormatter.normalizeInputOrNull(ngaySuaRaw)
                            ?: DateTextFormatter.formatForDisplay(ngaySuaRaw)
                        val ngaySuaChua = ngaySua.takeIf { it.isNotBlank() && it != "--" }
                        val ghiChu = value(row, header, DmbtLogColumns.GHI_CHU).trim()
                        val baseRecordId = value(row, header, DmbtLogColumns.RECORD_ID).trim().ifBlank {
                            buildDmbtFallbackRecordIdForTest(
                                maThietBi = maThietBi,
                                ngayPhatHien = ngayPhatHien,
                                hangMuc = hangMuc,
                                tinhTrangThietBi = tinhTrang,
                                rowNumber = rowNumber
                            )
                        }
                        val updatedAt = value(row, header, DmbtLogColumns.UPDATED_AT).trim().toLongOrNull()
                            ?: System.currentTimeMillis()
                        val identity = dmbtPulledRecordIdentity(sheetId, baseRecordId, namespaceRecordIds)
                        DeviceLog(
                            recordId = identity.recordId,
                            maThietBi = maThietBi,
                            hangMuc = hangMuc,
                            nguoiBaoCao = nguoiBaoCao,
                            tinhTrangThietBi = tinhTrang,
                            ktvPhuTrach = ktvPhuTrach,
                            ngayPhatHien = ngayPhatHien,
                            ngaySuaChua = ngaySuaChua,
                            ghiChu = ghiChu,
                            updatedAt = updatedAt,
                            sourceSheetId = identity.sourceSheetId
                        )
                    }
            }

            private fun value(row: List<String>, header: Header, column: String): String {
                val index = header.indexByColumn[column] ?: return ""
                return row.getOrNull(index).orEmpty()
            }

            private fun detectHeader(gridRows: List<List<String>>): Header {
                val aliasByColumn = mapOf(
                    DmbtLogColumns.RECORD_ID to setOf("record_id", "id"),
                    DmbtLogColumns.MA_THIET_BI to setOf("ma_thiet_bi", "ma_thiet_bi_", "ma_thietbi", "ma_thiet_bi__"),
                    DmbtLogColumns.HANG_MUC to setOf("hang_muc", "hang_muc_"),
                    DmbtLogColumns.NGUOI_BAO_CAO to setOf("nguoi_bao_cao", "nguoi_bao_cao_"),
                    DmbtLogColumns.TINH_TRANG_THIET_BI to setOf("tinh_trang_thiet_bi", "tinh_trang_thiet_bi_"),
                    DmbtLogColumns.KTV_PHU_TRACH to setOf("ktv_phu_trach", "ktv_phu_trach_nhan_thong_tin", "ktv_phu_trach_"),
                    DmbtLogColumns.NGAY_PHAT_HIEN to setOf("ngay_phat_hien", "ngay_phat_hien_"),
                    DmbtLogColumns.NGAY_SUA_CHUA to setOf("ngay_sua_chua", "ngay_sua_chua_"),
                    DmbtLogColumns.GHI_CHU to setOf("ghi_chu", "ghi_chu_"),
                    DmbtLogColumns.UPDATED_AT to setOf("updated_at")
                )
                val required = listOf(
                    DmbtLogColumns.MA_THIET_BI,
                    DmbtLogColumns.HANG_MUC,
                    DmbtLogColumns.NGUOI_BAO_CAO,
                    DmbtLogColumns.TINH_TRANG_THIET_BI,
                    DmbtLogColumns.KTV_PHU_TRACH,
                    DmbtLogColumns.NGAY_PHAT_HIEN,
                    DmbtLogColumns.NGAY_SUA_CHUA,
                    DmbtLogColumns.GHI_CHU
                )
                val best = gridRows
                    .take(12)
                    .mapIndexedNotNull { rowIndex, row ->
                        val normalized = row.map(::normalizeLooseHeaderValue)
                        val indexByColumn = aliasByColumn.mapValues { (_, aliases) ->
                            normalized.indexOfFirst { aliases.contains(it) }.takeIf { it >= 0 }
                        }.mapNotNull { (column, index) ->
                            index?.let { column to it }
                        }.toMap()
                        val score = required.count { indexByColumn[it] != null }
                        if (score < 5 || indexByColumn[DmbtLogColumns.MA_THIET_BI] == null) return@mapIndexedNotNull null
                        Header(rowIndex, row.map { it.trim() }, indexByColumn, score)
                    }
                    .sortedWith(compareByDescending<Header> { it.score }.thenBy { it.rowIndex })
                    .firstOrNull()
                    ?: throw NonRetryableSyncException("Cannot detect DMBT_LOG header row for test parser.")
                return best
            }

            private data class Header(
                val rowIndex: Int,
                val rawHeaders: List<String>,
                val indexByColumn: Map<String, Int>,
                val score: Int
            )
        }
    }

    internal data class DmbtPullTarget(
        val sheetId: Int,
        val namespaceRecordIds: Boolean
    )

    internal data class DmbtPulledRecordIdentity(
        val recordId: String,
        val sourceSheetId: Int
    )

    internal data class DmbtPullSheetStats(
        val sheetId: Int,
        val sheetTitle: String,
        val fetchedRows: Int,
        val uniqueRemoteIds: Int,
        val duplicateRemoteIds: Int,
        val duplicateRemoteIdSamples: List<String>,
        val duplicateRemoteRowSamples: List<DmbtDuplicateRemoteIdSample>,
        val skippedInvalidRows: Int,
        val skippedInvalidRowSamples: List<Int>
    )

    internal data class DmbtDuplicateRemoteIdSample(
        val recordId: String,
        val rowNumbers: List<Int>
    )

    internal enum class DmbtSheetIssueType {
        DUPLICATE_IDENTITY,
        INVALID_DISCOVERY_DATE
    }

    internal data class DmbtSheetIssueReport(
        val type: DmbtSheetIssueType,
        val sheetId: Int,
        val sheetTitle: String,
        val deviceCode: String,
        val discoveryDate: String,
        val description: String,
        val rowNumbers: List<Int>
    )
}
