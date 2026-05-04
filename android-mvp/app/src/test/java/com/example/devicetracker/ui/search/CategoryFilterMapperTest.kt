package com.example.devicetracker.ui.search

import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryFilterMapperTest {

    @Test
    fun `buildTimelineFilterPresentation returns distinct sorted years with timeline baseline`() {
        val presentation = buildTimelineFilterPresentation(
            items = listOf(
                sampleLog(recordId = "1", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "2", ngayPhatHien = "9/10/2025"),
                sampleLog(recordId = "3", ngayPhatHien = "2024-01-07")
            ),
            selectedYear = null
        )

        assertTrue(presentation.years.containsAll(listOf(2024, 2025, 2026)))
        assertTrue(presentation.years.first() <= 2022)
        assertEquals(3, presentation.visibleItems.size)
    }

    @Test
    fun `buildTimelineFilterPresentation filters items by selected year`() {
        val presentation = buildTimelineFilterPresentation(
            items = listOf(
                sampleLog(recordId = "1", ngayPhatHien = "22/03/2026"),
                sampleLog(recordId = "2", ngayPhatHien = "12/12/2025")
            ),
            selectedYear = 2026
        )

        assertEquals(2026, presentation.selectedYear)
        assertEquals(listOf("1"), presentation.visibleItems.map { it.recordId })
    }

    @Test
    fun `buildTimelineFilterPresentation clears selection when year is missing`() {
        val presentation = buildTimelineFilterPresentation(
            items = listOf(sampleLog(recordId = "1", ngayPhatHien = "22/03/2026")),
            selectedYear = 2035
        )

        assertNull(presentation.selectedYear)
        assertEquals(1, presentation.visibleItems.size)
    }

    @Test
    fun `buildTimelineFilterPresentation can parse year from Date literal`() {
        val presentation = buildTimelineFilterPresentation(
            items = listOf(sampleLog(recordId = "1", ngayPhatHien = "Date(2026,3,13)")),
            selectedYear = 2026
        )

        assertEquals(2026, presentation.selectedYear)
        assertEquals(1, presentation.visibleItems.size)
    }

    @Test
    fun `buildTimelineFilterPresentation keeps global year options from additionalYears`() {
        val presentation = buildTimelineFilterPresentation(
            items = listOf(sampleLog(recordId = "1", ngayPhatHien = "12/12/2025")),
            selectedYear = null,
            additionalYears = listOf(2026)
        )

        assertTrue(presentation.years.contains(2026))
    }

    @Test
    fun `extractTimelineYears returns distinct sorted years`() {
        val years = extractTimelineYears(
            listOf(
                sampleLog(recordId = "1", ngayPhatHien = "22/03/2026"),
                sampleLog(recordId = "2", ngayPhatHien = "12/12/2025"),
                sampleLog(recordId = "3", ngayPhatHien = "22/03/2026")
            )
        )

        assertEquals(listOf(2025, 2026), years)
    }

    @Test
    fun `extractTimelineYears falls back to seed recordId year when discovery date is blank`() {
        val years = extractTimelineYears(
            listOf(
                sampleLog(
                    recordId = "seed-beta-dmbt-2026-r22",
                    ngayPhatHien = "",
                    ngaySuaChua = null
                )
            )
        )

        assertEquals(listOf(2026), years)
    }

    @Test
    fun `extractTimelineYears does not use updatedAt fallback when no usable date`() {
        val years = extractTimelineYears(
            listOf(
                sampleLog(
                    recordId = "manual-record-no-year",
                    ngayPhatHien = "",
                    ngaySuaChua = null
                )
            )
        )

        assertTrue(years.isEmpty())
    }

    @Test
    fun `buildMaintenanceCategoryPresentation defaults to yearly DMBT only`() {
        val presentation = buildMaintenanceCategoryPresentation(
            items = listOf(
                sampleLog(recordId = "seed-beta-dmbt-2026-r4", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "seed-beta-dmbt-t4-2026-r4", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "seed-beta-sua-chua-t4-2026-r4", ngayPhatHien = "13/04/2026")
            ),
            selectedCategoryId = CATEGORY_YEARLY_ALL
        )

        assertEquals(CATEGORY_YEARLY_ALL, presentation.selectedCategoryId)
        assertEquals(listOf("seed-beta-dmbt-2026-r4"), presentation.visibleItems.map { it.recordId })
    }

    @Test
    fun `buildMaintenanceCategoryPresentation filters monthly DMBT records`() {
        val presentation = buildMaintenanceCategoryPresentation(
            items = listOf(
                sampleLog(recordId = "seed-beta-dmbt-2026-r4", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "seed-beta-dmbt-t4-2026-r4", ngayPhatHien = "13/04/2026")
            ),
            selectedCategoryId = CATEGORY_MONTHLY_DMBT_T4_2026
        )

        assertEquals(CATEGORY_MONTHLY_DMBT_T4_2026, presentation.selectedCategoryId)
        assertEquals(listOf("seed-beta-dmbt-t4-2026-r4"), presentation.visibleItems.map { it.recordId })
    }

    @Test
    fun `buildMaintenanceCategoryPresentation filters monthly repair records`() {
        val presentation = buildMaintenanceCategoryPresentation(
            items = listOf(
                sampleLog(recordId = "seed-beta-dmbt-t4-2026-r4", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "seed-beta-sua-chua-t4-2026-r4", ngayPhatHien = "13/04/2026")
            ),
            selectedCategoryId = CATEGORY_MONTHLY_REPAIR_T4_2026
        )

        assertEquals(CATEGORY_MONTHLY_REPAIR_T4_2026, presentation.selectedCategoryId)
        assertEquals(listOf("seed-beta-sua-chua-t4-2026-r4"), presentation.visibleItems.map { it.recordId })
    }

    @Test
    fun `buildMaintenanceCategoryPresentation filters yearly DMBT by year from record id`() {
        val presentation = buildMaintenanceCategoryPresentation(
            items = listOf(
                sampleLog(recordId = "seed-beta-dmbt-2025-r4", ngayPhatHien = "13/04/2026"),
                sampleLog(recordId = "seed-beta-dmbt-2026-r4", ngayPhatHien = "13/04/2026")
            ),
            selectedCategoryId = yearlyCategoryId(2025)
        )

        assertEquals(yearlyCategoryId(2025), presentation.selectedCategoryId)
        assertEquals(listOf("seed-beta-dmbt-2025-r4"), presentation.visibleItems.map { it.recordId })
    }

    private fun sampleLog(
        recordId: String,
        ngayPhatHien: String,
        ngaySuaChua: String? = null,
        hangMuc: String = "Xuong 3,4"
    ): DeviceLog = DeviceLog(
        recordId = recordId,
        maThietBi = "TB001",
        hangMuc = hangMuc,
        nguoiBaoCao = "Nguyen Van A",
        tinhTrangThietBi = "Rung manh",
        ktvPhuTrach = "KTV A",
        ngayPhatHien = ngayPhatHien,
        ngaySuaChua = ngaySuaChua,
        ghiChu = "",
        updatedAt = 1L
    )
}
