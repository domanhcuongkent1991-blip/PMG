package com.example.devicetracker.data.remote

import com.example.devicetracker.data.sheet.SheetConfig
import com.example.devicetracker.data.sheet.SheetSyncMode
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            defaultCreateSheetId = 1383308512
        )

        assertEquals(listOf(849979183, 1383308512), grouped.keys.toList())
        assertEquals(listOf("from-2022"), grouped.getValue(849979183).map { it.recordId })
        assertEquals(listOf("new-local"), grouped.getValue(1383308512).map { it.recordId })
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
    fun groupDmbtLogsByTargetSheet_rejectsLocalLogsWithoutAnyTargetSheet() {
        val grouped = SheetsRemoteDataSource.groupDmbtLogsByTargetSheet(
            logs = listOf(sampleLog(recordId = "new-local", sourceSheetId = null)),
            defaultCreateSheetId = null
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
