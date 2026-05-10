package com.example.devicetracker.data.remote

import com.example.devicetracker.data.sheet.SheetConfig
import com.example.devicetracker.data.sheet.SheetSyncMode
import com.example.devicetracker.data.model.DmbtRepairUpdate
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SheetsRemoteDataSourceRecordIdTest {

    @Test
    fun buildNamespacedDmbtRecordId_prefixes_sheet_id_for_fallback_records() {
        val recordId = SheetsRemoteDataSource.buildNamespacedDmbtRecordId(
            sheetId = 849979183,
            recordId = "dmbt-auto-device-a-01_04_2026-dmbt"
        )

        assertEquals("readonly-dmbt-849979183-dmbt-auto-device-a-01_04_2026-dmbt", recordId)
    }

    @Test
    fun buildNamespacedDmbtRecordId_leaves_existing_remote_record_id_readable_but_namespaced() {
        val recordId = SheetsRemoteDataSource.buildNamespacedDmbtRecordId(
            sheetId = 1783863163,
            recordId = "DMBT-remote-row-1"
        )

        assertTrue(recordId.startsWith("readonly-dmbt-1783863163-"))
        assertTrue(recordId.endsWith("DMBT-remote-row-1"))
    }

    @Test
    fun groupDmbtLogsByTargetSheet_usesSourceSheetIdBeforeDefaultCreateTarget() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(
                sampleLog(recordId = "from-2022", sourceSheetId = 849979183),
                sampleLog(recordId = "new-local", sourceSheetId = null)
            ),
            defaultCreateSheetId = 1383308512,
            configuredDmbtSheetIds = setOf(849979183, 1383308512)
        )

        assertEquals(listOf(849979183), grouped.keys.toList())
        assertEquals(listOf("from-2022"), grouped.getValue(849979183).map { it.recordId })
    }

    @Test
    fun resolveDmbtSheetRecordId_stripsLegacySheetPrefixBeforeWritingBack() {
        val sheetRecordId = SheetsRemoteDataSource.resolveDmbtSheetRecordId(
            targetSheetId = 849979183,
            recordId = "readonly-dmbt-849979183-DMBT-remote-row-1"
        )

        assertEquals("DMBT-remote-row-1", sheetRecordId)
    }

    @Test
    fun resolveDmbtSheetRecordId_preservesReadonlyPrefixWhenTargetSheetDiffers() {
        val sheetRecordId = SheetsRemoteDataSource.resolveDmbtSheetRecordId(
            targetSheetId = 1383308512,
            recordId = "readonly-dmbt-849979183-seed-beta-dmbt-2022-r5"
        )

        assertEquals("readonly-dmbt-849979183-seed-beta-dmbt-2022-r5", sheetRecordId)
    }

    @Test
    fun groupDmbtLogsByTargetSheet_rejectsLocalLogsWithoutAnyTargetSheet() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "new-local", sourceSheetId = null)),
            defaultCreateSheetId = null
        )

        assertTrue(grouped.isEmpty())
    }

    @Test
    fun groupDmbtLogsByTargetSheet_routesReadonlyRecordToItsSheetWithoutDefaultFallback() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(
                sampleLog(recordId = "readonly-dmbt-989601207-seed-beta-dmbt-2025-r16", sourceSheetId = null)
            ),
            defaultCreateSheetId = 1383308512,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertEquals(listOf(989601207), grouped.keys.toList())
    }

    @Test
    fun groupDmbtLogsByTargetSheet_allowsDefaultOnlyForSafeAutoIdWhenMultiSheet() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "dmbt-auto-tb001-2025-01-09", sourceSheetId = null)),
            defaultCreateSheetId = 1383308512,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertEquals(listOf(1383308512), grouped.keys.toList())
    }

    @Test
    fun pushRouting_yearlySourceSheetId_routesOnlyToYearlySheet() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "seed-beta-dmbt-2025-r16", sourceSheetId = 989601207)),
            defaultCreateSheetId = 1383308512,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertEquals(listOf(989601207), grouped.keys.toList())
    }

    @Test
    fun pushRouting_monthlySourceSheetId_routesOnlyToMonthlySheet() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "seed-beta-dmbt-t5-2026-r4", sourceSheetId = 1383308512)),
            defaultCreateSheetId = 1607125070,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertEquals(listOf(1383308512), grouped.keys.toList())
    }

    @Test
    fun pushRouting_unknownProvenanceInMultiSheetMode_isSkippedFailSafe() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "manual-legacy-no-gid", sourceSheetId = null)),
            defaultCreateSheetId = 1607125070,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertTrue(grouped.isEmpty())
    }

    @Test
    fun pushRouting_sourceSheetIdNotInConfiguredSet_isSkippedFailSafe() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "legacy-source", sourceSheetId = 777777777)),
            defaultCreateSheetId = 1607125070,
            configuredDmbtSheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070, 1383308512)
        )

        assertTrue(grouped.isEmpty())
    }

    @Test
    fun dmbtPullTargets_useConfiguredBindingsAndNamespaceNonDefaultSheets() {
        val targets = SheetsRemoteDataSource.dmbtPullTargets(
            listOf(
                SheetConfig.DmbtSheetBinding(
                    sheetId = 849979183,
                    mode = SheetSyncMode.TWO_WAY,
                    isDefaultCreateTarget = false
                ),
                SheetConfig.DmbtSheetBinding(
                    sheetId = 1383308512,
                    mode = SheetSyncMode.TWO_WAY,
                    isDefaultCreateTarget = true
                ),
                SheetConfig.DmbtSheetBinding(
                    sheetId = 157327514,
                    mode = SheetSyncMode.TWO_WAY,
                    isDefaultCreateTarget = false
                )
            )
        )

        assertEquals(listOf(849979183, 1383308512, 157327514), targets.map { it.sheetId })
        assertEquals(listOf(true, false, true), targets.map { it.namespaceRecordIds })
    }

    @Test
    fun dmbtPulledRecordIdentity_preservesSourceSheetIdForMultipleSheets() {
        val identities = listOf(
            SheetsRemoteDataSource.dmbtPulledRecordIdentity(
                sheetId = 849979183,
                baseRecordId = "DMBT-2022-row-1",
                namespaceRecordIds = true
            ),
            SheetsRemoteDataSource.dmbtPulledRecordIdentity(
                sheetId = 1783863163,
                baseRecordId = "DMBT-2023-row-1",
                namespaceRecordIds = true
            ),
            SheetsRemoteDataSource.dmbtPulledRecordIdentity(
                sheetId = 1383308512,
                baseRecordId = "DMBT-current-row-1",
                namespaceRecordIds = false
            )
        )

        assertEquals(listOf(849979183, 1783863163, 1383308512), identities.map { it.sourceSheetId })
        assertEquals(
            listOf(
                "readonly-dmbt-849979183-DMBT-2022-row-1",
                "readonly-dmbt-1783863163-DMBT-2023-row-1",
                "DMBT-current-row-1"
            ),
            identities.map { it.recordId }
        )
    }

    @Test
    fun buildDmbtPullSheetStats_countsDuplicateRemoteIdentities() {
        val stats = SheetsRemoteDataSource.buildDmbtPullSheetStats(
            sheetId = 1607125070,
            sheetTitle = "DMBT 2026",
            pulledLogs = listOf(
                sampleLog(recordId = "row-1", sourceSheetId = 1607125070),
                sampleLog(recordId = "row-1", sourceSheetId = 1607125070),
                sampleLog(recordId = "row-2", sourceSheetId = 1607125070)
            ),
            skippedInvalidRows = 1,
            rowNumbersByRecordId = mapOf("row-1" to listOf(12, 18), "row-2" to listOf(21)),
            skippedInvalidRowSamples = listOf(99)
        )

        assertEquals(1607125070, stats.sheetId)
        assertEquals("DMBT 2026", stats.sheetTitle)
        assertEquals(3, stats.fetchedRows)
        assertEquals(2, stats.uniqueRemoteIds)
        assertEquals(1, stats.duplicateRemoteIds)
        assertEquals(listOf("row-1"), stats.duplicateRemoteIdSamples)
        assertEquals(
            listOf(SheetsRemoteDataSource.DmbtDuplicateRemoteIdSample("row-1", listOf(12, 18))),
            stats.duplicateRemoteRowSamples
        )
        assertEquals(1, stats.skippedInvalidRows)
        assertEquals(listOf(99), stats.skippedInvalidRowSamples)
    }

    @Test
    fun buildDmbtSheetIssueReports_formatsDuplicateIssueForUser() {
        val duplicate = sampleLog(
            recordId = "row-1",
            sourceSheetId = 1607125070
        ).copy(
            maThietBi = "474FN02",
            ngayPhatHien = "08/04/2023",
            tinhTrangThietBi = "goi quat ro dau dang tham qua mat bich 2 nua"
        )
        val stats = SheetsRemoteDataSource.buildDmbtPullSheetStats(
            sheetId = 1607125070,
            sheetTitle = "DMBT 2026",
            pulledLogs = listOf(
                duplicate,
                duplicate.copy(),
                sampleLog(recordId = "row-2", sourceSheetId = 1607125070)
            ),
            skippedInvalidRows = 0,
            rowNumbersByRecordId = mapOf("row-1" to listOf(149, 151))
        )

        val issues = SheetsRemoteDataSource.buildDmbtSheetIssueReports(
            sheetTitle = "DMBT 2026",
            stats = stats,
            pulledLogs = listOf(duplicate, duplicate.copy())
        )

        assertEquals(1, issues.size)
        assertEquals("DMBT 2026", issues.first().sheetTitle)
        assertEquals("474FN02", issues.first().deviceCode)
        assertEquals("08/04/2023", issues.first().discoveryDate)
        assertEquals("goi quat ro dau dang tham qua mat bich 2 nua", issues.first().description)
        assertEquals(listOf(149, 151), issues.first().rowNumbers)
    }

    @Test
    fun pullDmbt_withoutRecordIdAndUpdatedAt_stillBuildsSourceSheetId() {
        val gridRows = listOf(
            listOf("DMBT 2025"),
            listOf(
                "STT",
                "hang_muc",
                "nguoi_bao_cao",
                "ma_thiet_bi",
                "tinh_trang_thiet_bi",
                "ktv_phu_trach",
                "ngay_phat_hien",
                "ngay_sua_chua",
                "ghi_chu"
            ),
            listOf(
                "1",
                "LÃ² 3,4",
                "Nguyen A",
                "463KL01",
                "1 tam lot VBD 1 bi gay",
                "KTV B",
                "07/01/2025",
                "",
                "from sheet"
            )
        )

        val logs = SheetsRemoteDataSource.parsePulledDmbtRowsForTest(
            sheetId = 989601207,
            gridRows = gridRows,
            namespaceRecordIds = true
        )

        assertEquals(1, logs.size)
        assertEquals(989601207, logs.first().sourceSheetId)
        assertTrue(logs.first().recordId.startsWith("readonly-dmbt-989601207-dmbt-auto-"))
    }

    @Test
    fun dedupeDmbtLogsForPush_keepsLatestLogPerTargetSheetRecordId() {
        val older = sampleLog(
            recordId = "readonly-dmbt-849979183-DMBT-2022-row-1",
            sourceSheetId = 849979183,
            updatedAt = 1000L,
            ghiChu = "old note"
        )
        val newer = older.copy(updatedAt = 2000L, ghiChu = "new note")
        val other = sampleLog(
            recordId = "readonly-dmbt-849979183-DMBT-2022-row-2",
            sourceSheetId = 849979183,
            updatedAt = 1500L,
            ghiChu = "other note"
        )

        val deduped = SheetsRemoteDataSource.dedupeDmbtLogsForPush(
            targetSheetId = 849979183,
            logs = listOf(older, other, newer)
        )

        assertEquals(listOf("readonly-dmbt-849979183-DMBT-2022-row-2", "readonly-dmbt-849979183-DMBT-2022-row-1"), deduped.map { it.recordId })
        assertEquals(listOf("other note", "new note"), deduped.map { it.ghiChu })
    }

    @Test
    fun dedupeDmbtLogsForPush_treatsReadonlyAndBaseRecordIdAsSameSheetRow() {
        val base = sampleLog(
            recordId = "seed-beta-dmbt-2022-r5",
            sourceSheetId = 849979183,
            updatedAt = 1000L,
            ghiChu = "old"
        )
        val namespaced = sampleLog(
            recordId = "readonly-dmbt-849979183-seed-beta-dmbt-2022-r5",
            sourceSheetId = 849979183,
            updatedAt = 2000L,
            ghiChu = "new"
        )

        val deduped = SheetsRemoteDataSource.dedupeDmbtLogsForPush(
            targetSheetId = 849979183,
            logs = listOf(base, namespaced)
        )

        assertEquals(1, deduped.size)
        assertEquals("new", deduped.first().ghiChu)
    }

    @Test
    fun yearlyMissingTitle_shouldBeFatal() {
        val fatal = SheetsRemoteDataSource.isYearlyDmbtSheetFailureFatal(
            sheetId = 989601207,
            yearlySheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070)
        )

        assertTrue(fatal)
    }

    @Test
    fun monthlyMissingTitle_shouldNotBeFatal() {
        val fatal = SheetsRemoteDataSource.isYearlyDmbtSheetFailureFatal(
            sheetId = 1383308512,
            yearlySheetIds = setOf(849979183, 1783863163, 1224276666, 989601207, 1607125070)
        )

        assertFalse(fatal)
    }

    @Test
    fun repairMonthlyFailure_optional_returnsEmptySuccess() {
        val failure = Result.failure<List<DmbtRepairUpdate>>(IllegalStateException("missing title"))
        val resolved = SheetsRemoteDataSource.resolveOptionalRepairPullResult(
            result = failure,
            optional = true
        )

        assertTrue(resolved.isSuccess)
        assertTrue(resolved.getOrNull().isNullOrEmpty())
    }

    @Test
    fun repairMonthlyFailure_required_stillFails() {
        val failure = Result.failure<List<DmbtRepairUpdate>>(IllegalStateException("parse error"))
        val resolved = SheetsRemoteDataSource.resolveOptionalRepairPullResult(
            result = failure,
            optional = false
        )

        assertTrue(resolved.isFailure)
    }

    @Test
    fun fallbackKeyLookup_whenAmbiguous_shouldFailSafeAndThrow() {
        try {
            SheetsRemoteDataSource.resolveFallbackRowNumber(
                fallbackKey = "tb001|2025_01_07|lo_3_4|1_tam_lot",
                rowByFallbackKey = mapOf("tb001|2025_01_07|lo_3_4|1_tam_lot" to 12),
                ambiguousFallbackKeys = setOf("tb001|2025_01_07|lo_3_4|1_tam_lot")
            )
            fail("Expected NonRetryableSyncException for ambiguous fallback key")
        } catch (e: NonRetryableSyncException) {
            assertTrue(e.message.orEmpty().contains("Ambiguous DMBT fallback key"))
        }
    }

    @Test
    fun validateDmbtProvenanceConfig_fails_whenNoBindings() {
        val result = SheetsRemoteDataSource.validateDmbtProvenanceConfig(
            bindings = emptyList(),
            yearlySheetIds = emptySet()
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty()
                .contains("Missing DMBT sheet bindings in config.")
        )
    }

    @Test
    fun validateDmbtProvenanceConfig_fails_whenMonthlyOnlyWithoutYearly() {
        val result = SheetsRemoteDataSource.validateDmbtProvenanceConfig(
            bindings = listOf(
                SheetConfig.DmbtSheetBinding(
                    sheetId = 1383308512,
                    mode = SheetSyncMode.TWO_WAY,
                    isDefaultCreateTarget = true
                )
            ),
            yearlySheetIds = emptySet()
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty()
                .contains("Monthly-only config is not allowed")
        )
    }

    private fun sampleLog(recordId: String, sourceSheetId: Int?): DeviceLog = sampleLog(
        recordId = recordId,
        sourceSheetId = sourceSheetId,
        updatedAt = 1713780000000,
        ghiChu = "note"
    )

    private fun sampleLog(
        recordId: String,
        sourceSheetId: Int?,
        updatedAt: Long,
        ghiChu: String
    ): DeviceLog = DeviceLog(
        recordId = recordId,
        maThietBi = "TB-001",
        hangMuc = "Khu A",
        nguoiBaoCao = "Tester",
        tinhTrangThietBi = "Can check",
        ktvPhuTrach = "KTV A",
        ngayPhatHien = "2026-04-22",
        ngaySuaChua = null,
        ghiChu = ghiChu,
        updatedAt = updatedAt,
        sourceSheetId = sourceSheetId
    )
}

