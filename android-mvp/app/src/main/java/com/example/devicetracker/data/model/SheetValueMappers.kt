package com.example.devicetracker.data.model

import com.example.devicetracker.data.sheet.DmbtLogColumns
import com.example.devicetracker.data.sheet.DmbtRepairLogColumns
import com.example.devicetracker.data.sheet.HgtCheckColumns
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.HgtCheck

data class DmbtRepairUpdate(
    val recordId: String,
    val maThietBi: String,
    val ngaySuaChua: String?,
    val ghiChu: String,
    val updatedAt: Long
)

fun DeviceLog.toDmbtLogRow(): Map<String, String> = linkedMapOf(
    DmbtLogColumns.RECORD_ID to recordId,
    DmbtLogColumns.MA_THIET_BI to maThietBi,
    DmbtLogColumns.HANG_MUC to hangMuc,
    DmbtLogColumns.NGUOI_BAO_CAO to nguoiBaoCao,
    DmbtLogColumns.TINH_TRANG_THIET_BI to tinhTrangThietBi,
    DmbtLogColumns.KTV_PHU_TRACH to ktvPhuTrach,
    DmbtLogColumns.NGAY_PHAT_HIEN to ngayPhatHien,
    DmbtLogColumns.NGAY_SUA_CHUA to (ngaySuaChua ?: ""),
    DmbtLogColumns.GHI_CHU to ghiChu,
    DmbtLogColumns.UPDATED_AT to updatedAt.toString()
)

fun Map<String, String>.toDeviceLogFromDmbtRow(): Result<DeviceLog> {
    val requiredColumns = listOf(
        DmbtLogColumns.RECORD_ID,
        DmbtLogColumns.MA_THIET_BI,
        DmbtLogColumns.HANG_MUC,
        DmbtLogColumns.NGUOI_BAO_CAO,
        DmbtLogColumns.TINH_TRANG_THIET_BI,
        DmbtLogColumns.KTV_PHU_TRACH,
        DmbtLogColumns.NGAY_PHAT_HIEN,
        DmbtLogColumns.NGAY_SUA_CHUA,
        DmbtLogColumns.GHI_CHU,
        DmbtLogColumns.UPDATED_AT
    )

    val missingColumns = requiredColumns.filterNot { containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return Result.failure(
            IllegalArgumentException("Missing required DMBT_LOG columns: ${missingColumns.joinToString(", ")}")
        )
    }

    val recordId = getValue(DmbtLogColumns.RECORD_ID).trim()
    val maThietBi = getValue(DmbtLogColumns.MA_THIET_BI).trim()
    val ngayPhatHien = getValue(DmbtLogColumns.NGAY_PHAT_HIEN).trim()

    if (recordId.isBlank()) {
        return Result.failure(IllegalArgumentException("record_id cannot be blank."))
    }
    if (maThietBi.isBlank()) {
        return Result.failure(IllegalArgumentException("ma_thiet_bi cannot be blank."))
    }
    if (ngayPhatHien.isBlank()) {
        return Result.failure(IllegalArgumentException("ngay_phat_hien cannot be blank."))
    }

    val updatedAtRaw = getValue(DmbtLogColumns.UPDATED_AT).trim()
    val updatedAt = updatedAtRaw.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("updated_at must be a Unix epoch (Long)."))

    val repairDate = getValue(DmbtLogColumns.NGAY_SUA_CHUA).trim().ifBlank { null }
    return Result.success(
        DeviceLog(
            recordId = recordId,
            maThietBi = maThietBi,
            hangMuc = getValue(DmbtLogColumns.HANG_MUC).trim(),
            nguoiBaoCao = getValue(DmbtLogColumns.NGUOI_BAO_CAO).trim(),
            tinhTrangThietBi = getValue(DmbtLogColumns.TINH_TRANG_THIET_BI).trim(),
            ktvPhuTrach = getValue(DmbtLogColumns.KTV_PHU_TRACH).trim(),
            ngayPhatHien = ngayPhatHien,
            ngaySuaChua = repairDate,
            ghiChu = getValue(DmbtLogColumns.GHI_CHU).trim(),
            updatedAt = updatedAt
        )
    )
}

fun DmbtRepairUpdate.toDmbtRepairLogRow(): Map<String, String> = linkedMapOf(
    DmbtRepairLogColumns.RECORD_ID to recordId,
    DmbtRepairLogColumns.MA_THIET_BI to maThietBi,
    DmbtRepairLogColumns.NGAY_SUA_CHUA to (ngaySuaChua ?: ""),
    DmbtRepairLogColumns.GHI_CHU to ghiChu,
    DmbtRepairLogColumns.UPDATED_AT to updatedAt.toString()
)

fun Map<String, String>.toDmbtRepairUpdateFromRow(): Result<DmbtRepairUpdate> {
    val requiredColumns = listOf(
        DmbtRepairLogColumns.RECORD_ID,
        DmbtRepairLogColumns.MA_THIET_BI,
        DmbtRepairLogColumns.NGAY_SUA_CHUA,
        DmbtRepairLogColumns.GHI_CHU,
        DmbtRepairLogColumns.UPDATED_AT
    )

    val missingColumns = requiredColumns.filterNot { containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return Result.failure(
            IllegalArgumentException("Missing required DMBT_REPAIR_LOG columns: ${missingColumns.joinToString(", ")}")
        )
    }

    val recordId = getValue(DmbtRepairLogColumns.RECORD_ID).trim()
    val maThietBi = getValue(DmbtRepairLogColumns.MA_THIET_BI).trim()
    val updatedAt = getValue(DmbtRepairLogColumns.UPDATED_AT).trim().toLongOrNull()

    if (recordId.isBlank()) return Result.failure(IllegalArgumentException("record_id cannot be blank."))
    if (maThietBi.isBlank()) return Result.failure(IllegalArgumentException("ma_thiet_bi cannot be blank."))
    if (updatedAt == null) {
        return Result.failure(IllegalArgumentException("updated_at must be a Unix epoch (Long)."))
    }

    return Result.success(
        DmbtRepairUpdate(
            recordId = recordId,
            maThietBi = maThietBi,
            ngaySuaChua = getValue(DmbtRepairLogColumns.NGAY_SUA_CHUA).trim().ifBlank { null },
            ghiChu = getValue(DmbtRepairLogColumns.GHI_CHU).trim(),
            updatedAt = updatedAt
        )
    )
}

fun HgtCheck.toHgtRow(): Map<String, String> = linkedMapOf(
    HgtCheckColumns.RECORD_ID to id,
    HgtCheckColumns.MA_THIET_BI to maThietBi,
    HgtCheckColumns.CHU_KY_NGAY to chuKyNgay.toString(),
    HgtCheckColumns.LAN_GAN_NHAT to lanGanNhat,
    HgtCheckColumns.LAN_TIEP_THEO to lanTiepTheo,
    HgtCheckColumns.UPDATED_AT to updatedAt.toString()
)

fun Map<String, String>.toHgtCheckFromRow(): Result<HgtCheck> {
    val requiredColumns = listOf(
        HgtCheckColumns.RECORD_ID,
        HgtCheckColumns.MA_THIET_BI,
        HgtCheckColumns.CHU_KY_NGAY,
        HgtCheckColumns.LAN_GAN_NHAT,
        HgtCheckColumns.LAN_TIEP_THEO,
        HgtCheckColumns.UPDATED_AT
    )

    val missingColumns = requiredColumns.filterNot { containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return Result.failure(
            IllegalArgumentException("Missing required HGT_CHECKS columns: ${missingColumns.joinToString(", ")}")
        )
    }

    val id = getValue(HgtCheckColumns.RECORD_ID).trim()
    val maThietBi = getValue(HgtCheckColumns.MA_THIET_BI).trim()
    val chuKyNgay = getValue(HgtCheckColumns.CHU_KY_NGAY).trim().toIntOrNull()
    val lanGanNhat = getValue(HgtCheckColumns.LAN_GAN_NHAT).trim()
    val lanTiepTheo = getValue(HgtCheckColumns.LAN_TIEP_THEO).trim()
    val updatedAt = getValue(HgtCheckColumns.UPDATED_AT).trim().toLongOrNull()

    if (id.isBlank()) return Result.failure(IllegalArgumentException("record_id cannot be blank."))
    if (maThietBi.isBlank()) return Result.failure(IllegalArgumentException("ma_thiet_bi cannot be blank."))
    if (chuKyNgay == null || chuKyNgay <= 0) {
        return Result.failure(IllegalArgumentException("chu_ky_ngay must be a positive integer."))
    }
    if (lanGanNhat.isBlank()) return Result.failure(IllegalArgumentException("lan_gan_nhat cannot be blank."))
    if (updatedAt == null) return Result.failure(IllegalArgumentException("updated_at must be a Unix epoch (Long)."))

    return Result.success(
        HgtCheck(
            id = id,
            maThietBi = maThietBi,
            chuKyNgay = chuKyNgay,
            lanGanNhat = lanGanNhat,
            lanTiepTheo = lanTiepTheo,
            updatedAt = updatedAt
        )
    )
}
