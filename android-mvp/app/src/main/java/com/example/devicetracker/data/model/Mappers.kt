package com.example.devicetracker.data.model

import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.domain.model.DeviceLog

fun DeviceLogEntity.toDomain(): DeviceLog = DeviceLog(
    recordId = recordId,
    maThietBi = maThietBi,
    hangMuc = hangMuc,
    nguoiBaoCao = nguoiBaoCao,
    tinhTrangThietBi = tinhTrangThietBi,
    ktvPhuTrach = ktvPhuTrach,
    ngayPhatHien = ngayPhatHien,
    ngaySuaChua = ngaySuaChua,
    ghiChu = ghiChu,
    updatedAt = updatedAt,
    sourceSheetId = sourceSheetId
)

fun DeviceLog.toEntity(syncStatus: String = "PENDING"): DeviceLogEntity = DeviceLogEntity(
    recordId = recordId,
    maThietBi = maThietBi,
    hangMuc = hangMuc,
    nguoiBaoCao = nguoiBaoCao,
    tinhTrangThietBi = tinhTrangThietBi,
    ktvPhuTrach = ktvPhuTrach,
    ngayPhatHien = ngayPhatHien,
    ngaySuaChua = ngaySuaChua,
    ghiChu = ghiChu,
    updatedAt = updatedAt,
    sourceSheetId = sourceSheetId,
    syncStatus = syncStatus
)
