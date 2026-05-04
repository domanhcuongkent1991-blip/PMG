package com.example.devicetracker.domain.model

data class DeviceLog(
    val recordId: String,
    val maThietBi: String,
    val hangMuc: String,
    val nguoiBaoCao: String,
    val tinhTrangThietBi: String,
    val ktvPhuTrach: String,
    val ngayPhatHien: String,
    val ngaySuaChua: String?,
    val ghiChu: String,
    val updatedAt: Long,
    val sourceSheetId: Int? = null
) {
    val repairStatus: RepairFilter
        get() = if (ngaySuaChua.isNullOrBlank()) RepairFilter.PENDING else RepairFilter.REPAIRED
}
