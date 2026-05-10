package com.example.devicetracker.data.repository

import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.local.entity.SyncQueueEntity
import com.example.devicetracker.data.model.DmbtRepairUpdate
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class DeviceLogRepositorySyncRulesTest {

    @Test
    fun isAmbiguousDmbtFallbackMessage_onlyMatchesExpectedAmbiguousSignature() {
        assertTrue(
            isAmbiguousDmbtFallbackMessage(
                "Ambiguous DMBT fallback key for push: 'e2elo998|25_06_2026|jsjsjjss|jsjsjjssjnsnsn'. Skip append/update to avoid duplicate."
            )
        )
        assertFalse(isAmbiguousDmbtFallbackMessage("Timeout when calling Google Sheets API"))
        assertFalse(isAmbiguousDmbtFallbackMessage(null))
        assertFalse(isAmbiguousDmbtFallbackMessage(""))
    }

    @Test
    fun ignoreAmbiguousPendingRecord_deletesMatchingQueue_and_marksPendingLocalFailed() = runBlocking {
        val recordId = "dmbt-auto-e2elo998-25_06_2026-jsjsjjss-jsjsjjssjnsnsn"
        val deviceDao = FakeDeviceLogDao(
            mutableMapOf(
                recordId to sampleEntity(
                    recordId = recordId,
                    updatedAt = 100L,
                    syncStatus = "PENDING"
                )
            )
        )
        val queueDao = FakeSyncQueueDao(
            mutableListOf(
                SyncQueueEntity(
                    id = 1L,
                    recordId = recordId,
                    operation = "UPSERT_LOG",
                    createdAt = 1L,
                    lastError = "Ambiguous DMBT fallback key for push: 'e2elo998|25_06_2026|jsjsjjss|jsjsjjssjnsnsn'. Skip append/update to avoid duplicate."
                )
            )
        )
        val ignored = ignoreAmbiguousPendingRecord(
            recordId = recordId,
            deviceLogDao = deviceDao,
            syncQueueDao = queueDao
        )

        assertTrue(ignored)
        assertTrue(queueDao.getAll().isEmpty())
        assertEquals("FAILED", deviceDao.getById(recordId)?.syncStatus)
    }

    @Test
    fun ignoreAmbiguousPendingRecord_doesNotDeleteQueue_forNonAmbiguousError() = runBlocking {
        val recordId = "record-timeout"
        val deviceDao = FakeDeviceLogDao(
            mutableMapOf(
                recordId to sampleEntity(
                    recordId = recordId,
                    updatedAt = 100L,
                    syncStatus = "PENDING"
                )
            )
        )
        val queueDao = FakeSyncQueueDao(
            mutableListOf(
                SyncQueueEntity(
                    id = 2L,
                    recordId = recordId,
                    operation = "UPSERT_LOG",
                    createdAt = 2L,
                    lastError = "Timeout when calling Google Sheets API"
                )
            )
        )
        val ignored = ignoreAmbiguousPendingRecord(
            recordId = recordId,
            deviceLogDao = deviceDao,
            syncQueueDao = queueDao
        )

        assertFalse(ignored)
        assertEquals(1, queueDao.getAll().size)
        assertEquals("PENDING", deviceDao.getById(recordId)?.syncStatus)
    }

    @Test
    fun ignoreAmbiguousPendingRecord_onlyDeletesMatchingRecordIdQueue() = runBlocking {
        val targetRecordId = "record-ambiguous-target"
        val otherRecordId = "record-ambiguous-other"
        val deviceDao = FakeDeviceLogDao(
            mutableMapOf(
                targetRecordId to sampleEntity(
                    recordId = targetRecordId,
                    updatedAt = 100L,
                    syncStatus = "PENDING"
                ),
                otherRecordId to sampleEntity(
                    recordId = otherRecordId,
                    updatedAt = 110L,
                    syncStatus = "PENDING"
                )
            )
        )
        val queueDao = FakeSyncQueueDao(
            mutableListOf(
                SyncQueueEntity(
                    id = 3L,
                    recordId = targetRecordId,
                    operation = "UPSERT_LOG",
                    createdAt = 3L,
                    lastError = "Ambiguous DMBT fallback key for push: 'target'. Skip append/update to avoid duplicate."
                ),
                SyncQueueEntity(
                    id = 4L,
                    recordId = otherRecordId,
                    operation = "UPSERT_LOG",
                    createdAt = 4L,
                    lastError = "Ambiguous DMBT fallback key for push: 'other'. Skip append/update to avoid duplicate."
                )
            )
        )
        val ignored = ignoreAmbiguousPendingRecord(
            recordId = targetRecordId,
            deviceLogDao = deviceDao,
            syncQueueDao = queueDao
        )

        assertTrue(ignored)
        assertEquals(1, queueDao.getAll().size)
        assertEquals(otherRecordId, queueDao.getAll().first().recordId)
        assertEquals("FAILED", deviceDao.getById(targetRecordId)?.syncStatus)
        assertEquals("PENDING", deviceDao.getById(otherRecordId)?.syncStatus)
    }

    @Test
    fun mirrorDeleteSyncedRowsMissingFromRemoteSnapshot_deletesOnlyStaleSyncedSameSheet() = runBlocking {
        val sheet2025 = 989601207
        val dao = FakeDeviceLogDao(
            mutableMapOf(
                "r1" to sampleEntity("r1", 100L, "SYNCED", sourceSheetId = sheet2025),
                "r2" to sampleEntity("r2", 100L, "SYNCED", sourceSheetId = sheet2025),
                "r3" to sampleEntity("r3", 100L, "SYNCED", sourceSheetId = sheet2025)
            )
        )
        val remoteLogs = listOf(
            sampleDomainLog("r1", 200L).copy(sourceSheetId = sheet2025),
            sampleDomainLog("r2", 200L).copy(sourceSheetId = sheet2025)
        )

        mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(dao, remoteLogs)

        assertEquals(setOf("r1", "r2"), dao.allRecordIds())
        assertEquals(1, dao.deletedByRecordIdsCalls)
    }

    @Test
    fun mirrorDeleteSyncedRowsMissingFromRemoteSnapshot_keepsPendingAndFailed() = runBlocking {
        val sheet2025 = 989601207
        val dao = FakeDeviceLogDao(
            mutableMapOf(
                "r1" to sampleEntity("r1", 100L, "SYNCED", sourceSheetId = sheet2025),
                "r2" to sampleEntity("r2", 100L, "PENDING", sourceSheetId = sheet2025),
                "r3" to sampleEntity("r3", 100L, "FAILED", sourceSheetId = sheet2025)
            )
        )
        val remoteLogs = listOf(
            sampleDomainLog("r1", 200L).copy(sourceSheetId = sheet2025)
        )

        mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(dao, remoteLogs)

        assertEquals(setOf("r1", "r2", "r3"), dao.allRecordIds())
        assertEquals(0, dao.deletedByRecordIdsCalls)
    }

    @Test
    fun mirrorDeleteSyncedRowsMissingFromRemoteSnapshot_doesNotDeleteCrossSheet() = runBlocking {
        val sheet2025 = 989601207
        val sheet2026 = 1607125070
        val dao = FakeDeviceLogDao(
            mutableMapOf(
                "r1" to sampleEntity("r1", 100L, "SYNCED", sourceSheetId = sheet2025),
                "r2" to sampleEntity("r2", 100L, "SYNCED", sourceSheetId = sheet2025),
                "x1" to sampleEntity("x1", 100L, "SYNCED", sourceSheetId = sheet2026)
            )
        )
        val remoteLogs = listOf(
            sampleDomainLog("r1", 200L).copy(sourceSheetId = sheet2025)
        )

        mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(dao, remoteLogs)

        assertEquals(setOf("r1", "x1"), dao.allRecordIds())
        assertEquals(1, dao.deletedByRecordIdsCalls)
    }

    @Test
    fun mirrorDelete_doesNotDeleteLegacyLocal_whenRemoteNamespacedRowResolvesToLocalId() = runBlocking {
        val sheet2025 = 989601207
        val legacyLocalId = "seed-beta-dmbt-2025-r16"
        val dao = FakeDeviceLogDao(
            mutableMapOf(
                legacyLocalId to sampleEntity(
                    recordId = legacyLocalId,
                    updatedAt = 100L,
                    syncStatus = "SYNCED",
                    hangMuc = "Lo 3,4",
                    tinhTrang = "1 tam lot VBD 1 bi gay",
                    ngayPhatHien = "07/01/2025",
                    sourceSheetId = sheet2025
                )
            )
        )

        val resolvedRemotePresentBySheet = mapOf(
            sheet2025 to setOf(legacyLocalId)
        )

        mirrorDeleteSyncedRowsMissingFromRemoteSnapshot(
            deviceLogDao = dao,
            remotePresentLocalIdsBySheet = resolvedRemotePresentBySheet
        )

        assertEquals(setOf(legacyLocalId), dao.allRecordIds())
        assertEquals(0, dao.deletedByRecordIdsCalls)
    }

    @Test
    fun shouldMarkAsSynced_returns_true_only_when_updatedAt_matches() {
        val pushed = sampleDomainLog(recordId = "r1", updatedAt = 200L)
        val localSame = sampleEntity(recordId = "r1", updatedAt = 200L, syncStatus = "PENDING")
        val localNewer = sampleEntity(recordId = "r1", updatedAt = 300L, syncStatus = "PENDING")

        assertTrue(shouldMarkAsSynced(localSame, pushed))
        assertFalse(shouldMarkAsSynced(localNewer, pushed))
        assertFalse(shouldMarkAsSynced(null, pushed))
    }

    @Test
    fun shouldApplyRemoteLog_keeps_pending_local_changes() {
        val localPending = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "PENDING")
        val remote = sampleDomainLog(recordId = "r1", updatedAt = 700L)

        assertFalse(shouldApplyRemoteLog(localPending, remote))
    }

    @Test
    fun shouldApplyRemoteLog_keeps_failed_local_changes() {
        val localFailed = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "FAILED")
        val remote = sampleDomainLog(recordId = "r1", updatedAt = 700L)

        assertFalse(shouldApplyRemoteLog(localFailed, remote))
    }

    @Test
    fun shouldApplyRemoteLog_applies_when_local_is_synced_and_remote_is_newer_or_equal() {
        val localSynced = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "SYNCED")
        val remoteNewer = sampleDomainLog(recordId = "r1", updatedAt = 700L)
        val remoteEqual = sampleDomainLog(recordId = "r1", updatedAt = 500L)
        val remoteOlder = sampleDomainLog(recordId = "r1", updatedAt = 400L)

        assertTrue(shouldApplyRemoteLog(localSynced, remoteNewer))
        assertTrue(shouldApplyRemoteLog(localSynced, remoteEqual))
        assertFalse(shouldApplyRemoteLog(localSynced, remoteOlder))
    }

    @Test
    fun shouldApplyRemoteLog_appliesManualSheetEdit_whenUpdatedAtMissingButContentChanged() {
        val localSynced = sampleEntity(
            recordId = "seed-beta-dmbt-2026-r19",
            updatedAt = 1_000L,
            syncStatus = "SYNCED",
            hangMuc = "Xuong CLK 3,4",
            tinhTrang = "Truc vit tai phat ta tieng keu to. Kha nang do mon goi bac trung gian.",
            ngayPhatHien = "10/01/2026"
        )
        val remoteManualSheetEdit = sampleDomainLog(
            recordId = "readonly-dmbt-1607125070-seed-beta-dmbt-2026-r19",
            updatedAt = 0L,
            maThietBi = "754SC03",
            hangMuc = "Xuong CLK 3,4",
            tinhTrang = "Truc vit tai phat ta tieng keu to. Kha nang do mon goi bac trung gian.",
            ngayPhatHien = "10/01/2026",
            ngaySuaChua = "05/05/2026",
            ghiChu = "test"
        )

        assertTrue(shouldApplyRemoteLog(localSynced, remoteManualSheetEdit))
    }

    @Test
    fun shouldApplyRemoteLog_skipsMissingUpdatedAtRemote_whenContentSame() {
        val localSynced = sampleEntity(
            recordId = "r1",
            updatedAt = 1_000L,
            syncStatus = "SYNCED",
            ngaySuaChua = "05/05/2026",
            ghiChu = "test"
        )
        val remoteSame = sampleDomainLog(
            recordId = "r1",
            updatedAt = 0L,
            ngaySuaChua = "05/05/2026",
            ghiChu = "test"
        )

        assertFalse(shouldApplyRemoteLog(localSynced, remoteSame))
    }

    @Test
    fun shouldApplyRemoteLog_allowsProvenanceOnlyBackfill_whenLegacyLocalHasNullSource() {
        val localLegacy = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 1_000L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = null
        )
        val remoteSameContentWithSource = sampleDomainLog(
            recordId = "readonly-dmbt-989601207-seed-beta-dmbt-2025-r16",
            updatedAt = 0L,
            maThietBi = "TB001",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            ngaySuaChua = null,
            ghiChu = ""
        ).copy(sourceSheetId = 989601207)

        assertTrue(shouldApplyRemoteLog(localLegacy, remoteSameContentWithSource))
    }

    @Test
    fun shouldBackfillSourceSheetIdOnly_returnsFalse_whenLocalAlreadyHasSource() {
        val localWithSource = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 1_000L,
            syncStatus = "SYNCED",
            sourceSheetId = 989601207
        )
        val remote = sampleDomainLog(
            recordId = "readonly-dmbt-1783863163-seed-beta-dmbt-2025-r16",
            updatedAt = 0L
        ).copy(sourceSheetId = 1783863163)

        assertFalse(shouldBackfillSourceSheetIdOnly(localWithSource, remote))
    }

    @Test
    fun shouldBackfillSourceSheetIdOnly_returnsFalse_whenRemoteContentDiffers() {
        val localLegacy = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 2_000L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = null
        )
        val remoteOlderDifferentContent = sampleDomainLog(
            recordId = "readonly-dmbt-989601207-seed-beta-dmbt-2025-r16",
            updatedAt = 1_000L,
            hangMuc = "Lo 3,4",
            tinhTrang = "noi dung cu hon tren sheet",
            ngayPhatHien = "07/01/2025"
        ).copy(sourceSheetId = 989601207)

        assertFalse(shouldBackfillSourceSheetIdOnly(localLegacy, remoteOlderDifferentContent))
        assertFalse(shouldApplyRemoteLog(localLegacy, remoteOlderDifferentContent))
    }

    @Test
    fun shouldApplyRemoteLog_applies_for_new_local_record() {
        val remote = sampleDomainLog(recordId = "new", updatedAt = 100L)
        assertTrue(shouldApplyRemoteLog(null, remote))
    }

    @Test
    fun shouldFailRefreshAfterRepairFailure_whenRepairIsOptional_returnsFalse() {
        val failedRepair = Result.failure<Unit>(IllegalStateException("repair monthly missing title"))
        assertFalse(shouldFailRefreshAfterRepairFailure(failedRepair, repairIsOptional = true))
    }

    @Test
    fun shouldFailRefreshAfterRepairFailure_whenRepairIsMandatory_returnsTrue() {
        val failedRepair = Result.failure<Unit>(IllegalStateException("repair parse error"))
        assertTrue(shouldFailRefreshAfterRepairFailure(failedRepair, repairIsOptional = false))
    }

    @Test
    fun buildDmbtBusinessKey_normalizesDifferentDateFormats_toSameKey() {
        val keyFromSlashDate = buildDmbtBusinessKey(
            maThietBi = "743BC04",
            ngayPhatHien = "09/01/2025",
            hangMuc = "Hu bom",
            tinhTrangThietBi = "Ap suat thap"
        )
        val keyFromIsoDate = buildDmbtBusinessKey(
            maThietBi = "743BC04",
            ngayPhatHien = "2025-01-09",
            hangMuc = "Hu bom",
            tinhTrangThietBi = "Ap suat thap"
        )

        assertEquals(keyFromSlashDate, keyFromIsoDate)
    }

    @Test
    fun findUniqueBusinessKeyMatch_returnsNull_whenMultipleRowsShareDeviceCodeButDifferentIssue() {
        val candidates = listOf(
            sampleEntity(
                recordId = "r1",
                updatedAt = 10L,
                syncStatus = "SYNCED",
                hangMuc = "Loi A",
                tinhTrang = "Tinh trang A",
                ngayPhatHien = "2025-01-09"
            ),
            sampleEntity(
                recordId = "r2",
                updatedAt = 11L,
                syncStatus = "SYNCED",
                hangMuc = "Loi B",
                tinhTrang = "Tinh trang B",
                ngayPhatHien = "2025-01-09"
            )
        )

        val expected = buildDmbtBusinessKey(
            maThietBi = "TB001",
            ngayPhatHien = "2025-01-09",
            hangMuc = "Loi C",
            tinhTrangThietBi = "Tinh trang C"
        )

        assertNull(findUniqueBusinessKeyMatch(candidates, expected))
    }

    @Test
    fun findUniqueBusinessKeyMatch_returnsSingleMatch_forSameIssueDifferentRecordIdFormats() {
        val candidates = listOf(
            sampleEntity(
                recordId = "readonly-dmbt-849979183-seed-beta-dmbt-2022-r5",
                updatedAt = 10L,
                syncStatus = "SYNCED",
                hangMuc = "Bom",
                tinhTrang = "Hong"
            )
        )
        val expected = buildDmbtBusinessKey(
            maThietBi = "TB001",
            ngayPhatHien = "22/03/2026",
            hangMuc = "Bom",
            tinhTrangThietBi = "Hong"
        )

        val matched = findUniqueBusinessKeyMatch(candidates, expected)
        assertEquals("readonly-dmbt-849979183-seed-beta-dmbt-2022-r5", matched?.recordId)
    }

    @Test
    fun pullSameRowTwice_withRecordIdFormatChange_shouldResolveToExistingLocalRow() {
        val existingLocal = sampleEntity(
            recordId = "readonly-dmbt-849979183-seed-beta-dmbt-2022-r5",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Bom",
            tinhTrang = "Hong",
            ngayPhatHien = "2025-01-09"
        )
        val pulledRemote = sampleDomainLog(
            recordId = "seed-beta-dmbt-2022-r5",
            updatedAt = 200L,
            maThietBi = "TB001",
            hangMuc = "Bom",
            tinhTrang = "Hong",
            ngayPhatHien = "09/01/2025"
        )

        val resolved = findUniqueBusinessKeyMatch(
            candidates = listOf(existingLocal),
            expectedBusinessKey = pulledRemote.toBusinessKey(),
            remoteSourceSheetId = 849979183
        )

        assertEquals(existingLocal.recordId, resolved?.recordId)
    }

    @Test
    fun findUniqueBusinessKeyMatch_doesNotMergeWhenLocalSourceSheetDiffersFromRemote() {
        val existingLocal = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 1607125070
        )
        val expected = buildDmbtBusinessKey(
            maThietBi = "TB001",
            ngayPhatHien = "2025-01-07",
            hangMuc = "Lo 3,4",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay"
        )

        val resolved = findUniqueBusinessKeyMatch(
            candidates = listOf(existingLocal),
            expectedBusinessKey = expected,
            remoteSourceSheetId = 989601207
        )

        assertNull(resolved)
    }

    @Test
    fun monthlyRemote_withSameBusinessKeyAsYearlyLocal_doesNotMergeCrossStream() {
        val yearlyLocal = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 989601207
        )
        val expected = buildDmbtBusinessKey(
            maThietBi = "TB001",
            ngayPhatHien = "2025-01-07",
            hangMuc = "Lo 3,4",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay"
        )

        val resolved = findUniqueBusinessKeyMatch(
            candidates = listOf(yearlyLocal),
            expectedBusinessKey = expected,
            remoteSourceSheetId = 1383308512
        )

        assertNull(resolved)
    }

    @Test
    fun yearlyRemote_withSameBusinessKeyAsMonthlyLocal_doesNotMergeCrossStream() {
        val monthlyLocal = sampleEntity(
            recordId = "readonly-dmbt-1383308512-seed-beta-dmbt-t5-2026-r4",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 1383308512
        )
        val expected = buildDmbtBusinessKey(
            maThietBi = "TB001",
            ngayPhatHien = "2025-01-07",
            hangMuc = "Lo 3,4",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay"
        )

        val resolved = findUniqueBusinessKeyMatch(
            candidates = listOf(monthlyLocal),
            expectedBusinessKey = expected,
            remoteSourceSheetId = 989601207
        )

        assertNull(resolved)
    }

    @Test
    fun isLocalSourceCompatibleWithRemote_exactRecordIdMismatchAcrossStreams_returnsFalse() {
        assertFalse(
            isLocalSourceCompatibleWithRemote(
                localSourceSheetId = 989601207,
                remoteSourceSheetId = 1383308512
            )
        )
    }

    @Test
    fun case463KL01_remoteRepairFrom2025Sheet_matchesLegacyNullSourceByBusinessKey() {
        val legacyLocal = DeviceLogEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            maThietBi = "463KL01",
            hangMuc = "Lo 3,4",
            nguoiBaoCao = "Trinh Huu Cuong",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay",
            ktvPhuTrach = "Dao Van Thuan",
            ngayPhatHien = "07/01/2025",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 100L,
            sourceSheetId = null,
            syncStatus = "SYNCED"
        )
        val remote = DeviceLog(
            recordId = "readonly-dmbt-989601207-seed-beta-dmbt-2025-r16",
            maThietBi = "463KL01",
            hangMuc = "Lo 3,4",
            nguoiBaoCao = "Trinh Huu Cuong",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay",
            ktvPhuTrach = "Dao Van Thuan",
            ngayPhatHien = "2025-01-07",
            ngaySuaChua = "2026-05-05",
            ghiChu = "test",
            updatedAt = 200L,
            sourceSheetId = 989601207
        )

        val resolved = findUniqueBusinessKeyMatch(
            candidates = listOf(legacyLocal),
            expectedBusinessKey = remote.toBusinessKey(),
            remoteSourceSheetId = remote.sourceSheetId
        )

        assertEquals("seed-beta-dmbt-2025-r16", resolved?.recordId)
    }

    @Test
    fun resolveMonthlyRepairTarget_withoutRecordId_withUniqueBusinessKey_matchesMonthlyLocal() {
        val monthlyLocal = sampleEntity(
            recordId = "readonly-dmbt-1383308512-seed-beta-dmbt-t5-2026-r4",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 1383308512
        )
        val repair = DmbtRepairUpdate(
            recordId = "",
            maThietBi = "TB001",
            ngaySuaChua = "2026-05-05",
            ghiChu = "fixed",
            updatedAt = 0L,
            ngayPhatHien = "2025-01-07",
            hangMuc = "Lo 3,4",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay"
        )

        val resolved = resolveMonthlyRepairTargetRecordId(
            repairLog = repair,
            monthlyCandidates = listOf(monthlyLocal)
        )

        assertEquals("readonly-dmbt-1383308512-seed-beta-dmbt-t5-2026-r4", resolved)
    }

    @Test
    fun resolveMonthlyRepairTarget_withoutRecordId_withAmbiguousBusinessKey_returnsNull() {
        val monthlyA = sampleEntity(
            recordId = "readonly-dmbt-1383308512-r1",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 1383308512
        )
        val monthlyB = sampleEntity(
            recordId = "readonly-dmbt-1383308512-r2",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            hangMuc = "Lo 3,4",
            tinhTrang = "1 tam lot VBD 1 bi gay",
            ngayPhatHien = "07/01/2025",
            sourceSheetId = 1383308512
        )
        val repair = DmbtRepairUpdate(
            recordId = "",
            maThietBi = "TB001",
            ngaySuaChua = "2026-05-05",
            ghiChu = "fixed",
            updatedAt = 0L,
            ngayPhatHien = "07/01/2025",
            hangMuc = "Lo 3,4",
            tinhTrangThietBi = "1 tam lot VBD 1 bi gay"
        )

        val resolved = resolveMonthlyRepairTargetRecordId(
            repairLog = repair,
            monthlyCandidates = listOf(monthlyA, monthlyB)
        )

        assertNull(resolved)
    }

    @Test
    fun buildMergedSyncedEntityFromRemote_backfillsLegacyNullSourceSheetId_forYearlySheets2022to2025() {
        val yearlySheetIds = listOf(849979183, 1783863163, 1224276666, 989601207)
        yearlySheetIds.forEach { sheetId ->
            val legacyLocal = sampleEntity(
                recordId = "seed-beta-dmbt-legacy-r1",
                updatedAt = 100L,
                syncStatus = "SYNCED",
                sourceSheetId = null
            )
            val remote = sampleDomainLog(
                recordId = "readonly-dmbt-$sheetId-seed-beta-dmbt-legacy-r1",
                updatedAt = 200L,
                maThietBi = "TB001",
                hangMuc = "Lo 3,4",
                tinhTrang = "mat lot",
                ngayPhatHien = "07/01/2025"
            ).copy(sourceSheetId = sheetId)

            val merged = buildMergedSyncedEntityFromRemote(
                currentLocal = legacyLocal,
                remoteLog = remote,
                fallbackNowMillis = 999L
            )

            assertEquals("seed-beta-dmbt-legacy-r1", merged.recordId)
            assertEquals(sheetId, merged.sourceSheetId)
            assertEquals(200L, merged.updatedAt)
            assertEquals("SYNCED", merged.syncStatus)
        }
    }

    @Test
    fun buildMergedSyncedEntityFromRemote_preservesExistingSourceSheetId_whenAlreadySet() {
        val local = sampleEntity(
            recordId = "seed-beta-dmbt-2025-r16",
            updatedAt = 100L,
            syncStatus = "SYNCED",
            sourceSheetId = 989601207
        )
        val remote = sampleDomainLog(
            recordId = "readonly-dmbt-1783863163-seed-beta-dmbt-2025-r16",
            updatedAt = 200L
        ).copy(sourceSheetId = 1783863163)

        val merged = buildMergedSyncedEntityFromRemote(
            currentLocal = local,
            remoteLog = remote,
            fallbackNowMillis = 999L
        )

        assertEquals(989601207, merged.sourceSheetId)
    }

    private fun sampleEntity(
        recordId: String,
        updatedAt: Long,
        syncStatus: String,
        hangMuc: String = "Xuong",
        tinhTrang: String = "Tinh trang",
        ngayPhatHien: String = "22/03/2026",
        ngaySuaChua: String? = null,
        ghiChu: String = "",
        sourceSheetId: Int? = null
    ): DeviceLogEntity =
        DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = hangMuc,
            nguoiBaoCao = "A",
            tinhTrangThietBi = tinhTrang,
            ktvPhuTrach = "KTV",
            ngayPhatHien = ngayPhatHien,
            ngaySuaChua = ngaySuaChua,
            ghiChu = ghiChu,
            updatedAt = updatedAt,
            sourceSheetId = sourceSheetId,
            syncStatus = syncStatus
        )

    private fun sampleDomainLog(
        recordId: String,
        updatedAt: Long,
        maThietBi: String = "TB001",
        hangMuc: String = "Xuong",
        tinhTrang: String = "Tinh trang",
        ngayPhatHien: String = "22/03/2026",
        ngaySuaChua: String? = null,
        ghiChu: String = ""
    ): DeviceLog =
        DeviceLog(
            recordId = recordId,
            maThietBi = maThietBi,
            hangMuc = hangMuc,
            nguoiBaoCao = "A",
            tinhTrangThietBi = tinhTrang,
            ktvPhuTrach = "KTV",
            ngayPhatHien = ngayPhatHien,
            ngaySuaChua = ngaySuaChua,
            ghiChu = ghiChu,
            updatedAt = updatedAt
        )

    private class FakeDeviceLogDao(
        private val store: MutableMap<String, DeviceLogEntity> = mutableMapOf()
    ) : DeviceLogDao {
        var deletedByRecordIdsCalls: Int = 0
        override suspend fun countAll(): Int = store.size
        override suspend fun countBySyncStatus(syncStatus: String): Int = store.values.count { it.syncStatus == syncStatus }
        override fun observeAll(): Flow<List<DeviceLogEntity>> = flowOf(store.values.toList())
        override fun observeByDeviceCode(deviceCode: String, filter: String): Flow<List<DeviceLogEntity>> = flowOf(emptyList())
        override suspend fun getById(recordId: String): DeviceLogEntity? = store[recordId]
        override suspend fun getBySourceSheetAndDeviceCode(sourceSheetId: Int, deviceCode: String): List<DeviceLogEntity> = emptyList()
        override suspend fun getByDeviceCode(deviceCode: String): List<DeviceLogEntity> = emptyList()
        override suspend fun getPendingLogs(): List<DeviceLogEntity> = store.values.filter { it.syncStatus == "PENDING" }
        override suspend fun getAllRecordIds(): List<String> = store.keys.toList()
        override suspend fun getRecordIdsBySourceSheetId(sourceSheetId: Int): List<String> =
            store.values.filter { it.sourceSheetId == sourceSheetId }.map { it.recordId }
        override suspend fun getSyncedRecordIdsBySourceSheetId(sourceSheetId: Int): List<String> =
            store.values
                .filter { it.sourceSheetId == sourceSheetId && it.syncStatus == "SYNCED" }
                .map { it.recordId }
        override suspend fun deleteByRecordIds(recordIds: List<String>): Int {
            if (recordIds.isEmpty()) return 0
            deletedByRecordIdsCalls += 1
            var deleted = 0
            recordIds.forEach { id ->
                if (store.remove(id) != null) {
                    deleted += 1
                }
            }
            return deleted
        }
        override suspend fun upsert(entity: DeviceLogEntity) {
            store[entity.recordId] = entity
        }
        override suspend fun upsertAll(items: List<DeviceLogEntity>) {
            items.forEach { store[it.recordId] = it }
        }

        fun allRecordIds(): Set<String> = store.keys.toSet()
    }

    private class FakeSyncQueueDao(
        private val queue: MutableList<SyncQueueEntity> = mutableListOf()
    ) : SyncQueueDao {
        override suspend fun getAll(): List<SyncQueueEntity> = queue.sortedBy { it.createdAt }
        override suspend fun countAll(): Int = queue.size
        override suspend fun countWithErrors(): Int = queue.count { it.retryCount > 0 || !it.lastError.isNullOrBlank() }
        override suspend fun latestError(): String? = queue.lastOrNull { !it.lastError.isNullOrBlank() }?.lastError
        override suspend fun insert(item: SyncQueueEntity) {
            queue += item
        }
        override suspend fun markFailed(id: Long, error: String) {
            val idx = queue.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val current = queue[idx]
                queue[idx] = current.copy(retryCount = current.retryCount + 1, lastError = error)
            }
        }
        override suspend fun deleteById(id: Long) {
            queue.removeAll { it.id == id }
        }
        override suspend fun deleteByRecordId(recordId: String) {
            queue.removeAll { it.recordId == recordId }
        }
        override suspend fun deleteAmbiguousPushErrorByRecordId(recordId: String): Int {
            val before = queue.size
            queue.removeAll { item ->
                item.operation == "UPSERT_LOG" &&
                    item.recordId == recordId &&
                    item.lastError?.startsWith("Ambiguous DMBT fallback key for push:") == true
            }
            return before - queue.size
        }
    }

}
